package com.xenoamess.bbolt;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorTest {

    private static Path fixturePath() throws Exception {
        URL url = CursorTest.class.getResource("/meta.db");
        assertNotNull(url, "fixture meta.db not found");
        return Paths.get(url.toURI());
    }

    @Test
    void iterateRootBuckets() throws Exception {
        try (BboltDB db = BboltDB.open(fixturePath());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Set<String> names = new HashSet<>();
            Cursor cursor = tx.getRootBucket().cursor();
            for (Cursor.Entry entry = cursor.first(); entry != null; entry = cursor.next()) {
                assertTrue(entry.isBucket(), "root entries should be buckets");
                names.add(new String(entry.key()));
            }
            assertEquals(Set.of("v1"), names);
        }
    }

    @Test
    void seekAndNext() throws Exception {
        try (BboltDB db = BboltDB.open(fixturePath());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket images = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images");
            assertNotNull(images);

            Cursor cursor = images.cursor();
            Set<String> names = new HashSet<>();
            for (Cursor.Entry entry = cursor.first(); entry != null; entry = cursor.next()) {
                assertTrue(entry.isBucket());
                names.add(new String(entry.key()));
            }
            assertEquals(Set.of("alpine:3.20", "docker.io/alpine:3.20", "docker.io/library/alpine:3.20"), names);
        }
    }
}
