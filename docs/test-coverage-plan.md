# Test Coverage Improvement Plan — bbolt-java

Date: 2026-07-17
Status: approved, in execution

## 背景

基于 JaCoCo 覆盖率报告（77%）与真实用户场景（读取 containerd `meta.db`）的联合分析，
确定按场景价值（而非覆盖率数字）补充用例。分析结论：

- 两个低覆盖区域背后是真实产品问题：meta0 损坏时拒开（与 Go bbolt 行为不一致）、
  overflow 页读取完全未测（静默数据错误风险）。
- `toString()` / 琐碎 accessor 不单独补测，不为覆盖率凑数。

## 决策记录

- **Fixture 生成方式**：Docker(`golang:1.23` 镜像，本地已有）运行 Go + `go.etcd.io/bbolt`。
  本机无 Go 环境；生成器放 `tools/fixturegen/`，产物提交到 `src/test/resources/`,CI 不需要 Docker。
- **64K 页 fixture**:bbolt 创建 DB 时页大小取 OS 运行时，无法在 4K 页宿主机生成 64K 页 DB,
  改为生成器内用 Go 裸写字节 + stdlib `hash/fnv`（与 bbolt sum64 同为 FNV-1a 64）合成最小合法文件。
- **空叶页迭代路径**(`Cursor.java:61-62`):bbolt 提交时 rebalance 会合并空页，真实文件可能
  不可达；先尝试，不可达则定性防御代码，不手拼假文件硬追。
- **meta0 无效时的 pageSize 探测**：按 4K/8K/16K/32K/64K 候选逐个尝试（与 Go bbolt 思路一致）。
- **行为变更已获批准**:`open()` meta 恢复、接入 `Meta.validate()` 精确报错、EOF 包装为 `BboltException`。

## 实施步骤

### 1. Fixture 生成器 `tools/fixturegen/`

- `main.go` + `go.mod`(`go.etcd.io/bbolt`);Docker 运行,module 缓存挂载 `~/.cache/go-modules`,
  网络异常回退 `GOPROXY=https://goproxy.cn`。
- 产物（写入 `src/test/resources/`):
  - `overflow.db` — 10KB/20KB 大 value(跨 overflow 页)+ 普通 kv 混排
  - `empty-value.db` — `Put(k, nil)` 零长度 value + 普通 kv 混排
  - `sparse.db` — 大量 key 跨多叶页（prev 跨叶、last 多级下潜)
  - `pagesize-64k.db` — 裸写合成的最小合法 64K 页文件(双 meta + 单叶页)
- 损坏 meta 变体：不生成独立文件，Java 测试里复制后翻字节(沿用现有手法)。

### 2. 主代码修复 `BboltDB.open()`

- meta0 无效不拒开：按候选 pageSize 探测并校验 meta1;双有效选高 txid;
  全无效时经 `Meta.validate()` 抛精确原因(invalid magic / unsupported version / checksum mismatch)。
- 初始读取的 `IOException`(截断文件 EOF)包装为 `BboltException`。
- 不改公共 API 签名；空 value 的 `get` 返回 `byte[0]` 语义用测试钉住。

### 3. 测试用例(P0–P3)

| 优先级 | 用例 | 目标代码 |
| --- | --- | --- |
| P0 | overflow 大 value 读回一致、多个大 value、`get(String)` 大 value | `BboltDB.java:96-103` |
| P0 | meta0 magic 坏→meta1 打开;meta0 checksum 坏→meta1 打开;双有效选高 txid;双无效→精确异常;version≠2→精确异常 | `BboltDB.java:53-66`, `Meta.java:75,80-90` |
| P1 | 零长度 value 往返(返回 `byte[0]`) | `LeafPageElement.java:52-53` |
| P1 | 截断文件(1000 字节)→ `BboltException` | `BboltDB.java:27-28`, `BboltException(msg,cause)` |
| P1 | 64K pageSize DB 打开+读取 | `BboltDB.open()` 页大小路径 |
| P2 | `prev()` 跨叶页经 branch 回退;`last()` 多级 branch 下潜 | `Cursor.java:84-88`, `Cursor.java:37-41` |
| P2 | 空 inline bucket(value.length==16)游标为 null | `Bucket.java:27` |
| P3 | 畸形 inline bucket(inline 页为 branch)→ 防御性 `BboltException` | `Cursor.java:171` |

测试加固:现有两个 corruption 用例补异常消息断言;清理 `CursorTest.java:28-31` 死 lambda;
`path()/pageSize()/db()/sequence()/type()` 等顺手覆盖(合并进场景用例,不单独写)。

### 4. 验证

1. 本地 `mvn -B verify` 全绿;jacoco 确认 `BboltDB`/`Meta`/`Cursor`/`Page` 分支覆盖显著上升。
2. 推送后 CI 绿;`coverage-pages` 部署成功;badge 更新(注意 shields.io 缓存延迟)。
3. 项目笔记追加到 `docs/coverage-report-notes.md`;skill 侧结论在最终回复显式说明。

### 明确不做

- 不为 `toString()`/调试输出单独补测试。
- 不改公共 API 签名;不引入新运行时依赖(仅 JUnit + Docker 内的 bbolt)。
- 空叶页迭代路径若真实 fixture 不可达,不手拼假文件。
