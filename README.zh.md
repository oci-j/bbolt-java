# bbolt-java

> [English](README.md) | **简体中文**

[![Coverage](https://img.shields.io/endpoint?url=https://oci-j.github.io/bbolt-java/coverage.json)](https://oci-j.github.io/bbolt-java/report/coverage.html)

一个极简的只读 Java 读取器，用于读取 [etcd-io/bbolt](https://github.com/etcd-io/bbolt) 数据库文件。它不依赖原生 bbolt 库，可以直接在 JVM 上检查 bbolt 数据库。

## 特性

- 只读访问 bbolt 数据库文件。
- 无原生依赖。
- 支持 bucket 遍历、键值查找和游标迭代。
- 需要 Java 17+。

## Maven 依赖

```xml
<dependency>
    <groupId>com.xenoamess.oci-j</groupId>
    <artifactId>bbolt-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 用法

### 打开数据库

```java
import com.xenoamess.bbolt.BboltDB;
import com.xenoamess.bbolt.Bucket;
import com.xenoamess.bbolt.ReadOnlyTransaction;

import java.nio.file.Path;
import java.nio.file.Paths;

try (BboltDB db = BboltDB.open(Paths.get("/var/lib/docker/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db"));
     ReadOnlyTransaction tx = db.beginReadOnly()) {

    Bucket root = tx.getRootBucket();
    Bucket v1 = root.getBucket("v1");
    Bucket moby = v1.getBucket("moby");
    Bucket images = moby.getBucket("images");
    Bucket image = images.getBucket("alpine:3.20");
    Bucket target = image.getBucket("target");

    String digest = target.get("digest");
    String mediaType = target.get("mediatype");

    System.out.println("digest: " + digest);
    System.out.println("mediaType: " + mediaType);
}
```

### 遍历 Bucket

```java
try (BboltDB db = BboltDB.open(path);
     ReadOnlyTransaction tx = db.beginReadOnly()) {

    Bucket bucket = tx.getRootBucket().getBucket("v1");
    bucket.forEach(entry -> {
        System.out.println(new String(entry.key()) + " -> " + new String(entry.value()));
    });
}
```

## 支持的 Schema 路径

本库本身是通用的，但测试夹具和主要使用场景面向 containerd 的 `meta.db` 布局。常用路径包括：

- `v1/moby/images/<image-name>/target/digest`
- `v1/moby/images/<image-name>/target/mediatype`

这些路径用于从 containerd 的 metadata v1 bbolt 存储中读取镜像元数据。

## 构建

```bash
mvn -B verify
```

## 贡献

本仓库使用 Dependabot 进行依赖更新，并通过 GitHub Actions 工作流自动合并 patch/minor 版本升级。
auto-merge 工作流需要一个名为 `MYTOKEN` 的 Dependabot secret，权限如下：

- `contents: write`（用于合并 PR）
- `pull-requests: write`（用于开启 auto-merge 和审批）

更多细节见 `.github/workflows/auto-merge.yml`。

## 许可证

待定
