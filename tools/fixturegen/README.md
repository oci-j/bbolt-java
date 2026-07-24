# fixturegen

Generates the bbolt test fixtures under `src/test/resources/` using the real
Go bbolt implementation. Generated files are committed to the repo; CI does
not need Docker or Go.

使用真实的 Go bbolt 实现生成 `src/test/resources/` 下的测试 fixture。
生成产物已提交进仓库，CI 无需 Docker 或 Go 环境。

## Usage / 用法

```bash
mkdir -p ~/.cache/go-modules
docker run --rm \
  -v "$PWD:/work" -w /work/tools/fixturegen \
  -v "$HOME/.cache/go-modules:/go/pkg/mod" \
  -e GOPROXY=https://goproxy.cn,direct \
  golang:1.23 bash -c "go mod tidy && go run . /work/src/test/resources"
```

## Fixtures

| File | Contents | 内容 |
| --- | --- | --- |
| `overflow.db` | bucket `data`: `small`, `large-10k` (10240 B, `i%251`), `large-20k` (20480 B, `i%253`); bucket `nested`: `big` (8192 B, `i%241`) | 大 value 跨 overflow 页 |
| `empty-value.db` | bucket `markers`: `a`→nil, `b`→empty, `c`→`x` | 零长度 value |
| `sparse.db` | bucket `wide`: `key-0000`..`key-0499` → `val-XXXX` | 多叶页 B+ 树 |
| `pagesize-64k.db` | synthesized bytes (not via bbolt API): 64 KiB pages, 2 metas (txid 1/2), one leaf with `hello`→`world-64k-page`, `second`→`value2` | 裸写合成的 64K 页文件 |

Corrupted-meta variants are not generated here; tests copy a fixture and flip
bytes with `RandomAccessFile`. / 损坏 meta 的变体不在此生成，测试里复制后改字节。
