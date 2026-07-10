package com.xenoamess.bbolt;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BboltDBTest {

    private static Path fixturePath() throws Exception {
        URL url = BboltDBTest.class.getResource("/meta.db");
        assertNotNull(url, "fixture meta.db not found");
        return Paths.get(url.toURI());
    }

    @Test
    void openAndReadMeta() throws Exception {
        try (BboltDB db = BboltDB.open(fixturePath())) {
            Meta meta = db.meta();
            assertEquals(Meta.MAGIC, meta.magic());
            assertEquals(Meta.VERSION, meta.version());
            assertTrue(meta.pageSize() > 0);
            assertTrue(meta.txid() >= 0);
            assertTrue(meta.isValid());
            assertNotNull(meta.rootBucket());
        }
    }

    @Test
    void navigateToImageDigest() throws Exception {
        try (BboltDB db = BboltDB.open(fixturePath());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket root = tx.getRootBucket();
            Bucket v1 = root.getBucket("v1");
            assertNotNull(v1, "v1 bucket should exist");
            Bucket moby = v1.getBucket("moby");
            assertNotNull(moby, "moby bucket should exist");
            Bucket images = moby.getBucket("images");
            assertNotNull(images, "images bucket should exist");
            Bucket image = images.getBucket("alpine:3.20");
            assertNotNull(image, "alpine:3.20 bucket should exist");
            Bucket target = image.getBucket("target");
            assertNotNull(target, "target bucket should exist");
            byte[] digest = target.get("digest".getBytes());
            assertArrayEquals("sha256:abc123".getBytes(), digest);
            assertEquals("application/vnd.oci.image.index.v1+json", target.get("mediatype"));
        }
    }

    @Test
    void dockerIoLibraryImageName() throws Exception {
        try (BboltDB db = BboltDB.open(fixturePath());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            byte[] digest = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images")
                    .getBucket("docker.io/library/alpine:3.20")
                    .getBucket("target")
                    .get("digest".getBytes());
            assertArrayEquals("sha256:abc123".getBytes(), digest);
        }
    }
}
