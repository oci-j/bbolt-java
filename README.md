# bbolt-java

A minimal, read-only Java reader for [etcd-io/bbolt](https://github.com/etcd-io/bbolt) database files. It does not depend on the native bbolt library and can be used to inspect bbolt databases directly from the JVM.

## Features

- Read-only access to bbolt database files.
- No native dependencies.
- Supports bucket traversal, key/value lookup, and cursor iteration.
- Works with Java 17+.

## Maven Dependency

```xml
<dependency>
    <groupId>com.xenoamess.oci-j</groupId>
    <artifactId>bbolt-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

### Open a Database

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

### Iterate over a Bucket

```java
try (BboltDB db = BboltDB.open(path);
     ReadOnlyTransaction tx = db.beginReadOnly()) {

    Bucket bucket = tx.getRootBucket().getBucket("v1");
    bucket.forEach(entry -> {
        System.out.println(new String(entry.key()) + " -> " + new String(entry.value()));
    });
}
```

## Supported Schema Paths

The library itself is generic, but the test fixtures and primary use case target containerd's `meta.db` layout. The commonly used paths include:

- `v1/moby/images/<image-name>/target/digest`
- `v1/moby/images/<image-name>/target/mediatype`

These paths are used to read image metadata from containerd's metadata v1 bbolt store.

## Building

```bash
mvn -B verify
```

## Contributing

This repository uses Dependabot for dependency updates and a GitHub Actions workflow to automatically merge patch/minor version bumps.
The auto-merge workflow requires a Dependabot secret named `MYTOKEN` with the following permissions:

- `contents: write` (to merge the pull request)
- `pull-requests: write` (to enable auto-merge and approve)

For more details, see `.github/workflows/dependabot-auto-merge.yml`.

## License

TBD
