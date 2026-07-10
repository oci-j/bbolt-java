package com.xenoamess.bbolt;

import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BboltDBAdvancedTest {

    private static Path fixture(String name) throws Exception {
        URL url = BboltDBAdvancedTest.class.getResource("/" + name);
        assertNotNull(url, "fixture " + name + " not found");
        return Paths.get(url.toURI());
    }

    @Test
    void emptyBucketHasNoEntries() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("empty.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket images = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images");
            assertNotNull(images);

            Cursor cursor = images.cursor();
            assertNull(cursor.first(), "empty bucket should have no first entry");
            assertNull(cursor.next(), "empty bucket should have no next entry");
            assertNull(cursor.last(), "empty bucket should have no last entry");
            assertNull(images.get("anything".getBytes()), "get should return null");
        }
    }

    @Test
    void missingKeyAndBucketReturnNull() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("meta.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket images = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images");
            assertNull(images.get("nonexistent:tag".getBytes()));
            assertNull(images.getBucket("nonexistent:tag"));
        }
    }

    @Test
    void cursorLastAndPrev() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("meta.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket images = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images");
            Cursor cursor = images.cursor();
            Cursor.Entry last = cursor.last();
            assertNotNull(last);
            Set<String> expected = Set.of(
                    "alpine:3.20",
                    "docker.io/alpine:3.20",
                    "docker.io/library/alpine:3.20"
            );
            assertTrue(expected.contains(new String(last.key())));

            List<String> reversed = new ArrayList<>();
            reversed.add(new String(last.key()));
            for (Cursor.Entry e = cursor.prev(); e != null; e = cursor.prev()) {
                reversed.add(new String(e.key()));
            }
            assertEquals(3, reversed.size());
        }
    }

    @Test
    void seekNotFoundReturnsNext() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("meta.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket images = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images");
            Cursor cursor = images.cursor();

            Cursor.Entry afterAll = cursor.seek("zzzz".getBytes());
            assertNull(afterAll, "seek after all keys should return null");

            Cursor.Entry firstOrAfter = cursor.seek("a".getBytes());
            assertNotNull(firstOrAfter);
            assertEquals("alpine:3.20", new String(firstOrAfter.key()));
        }
    }

    @Test
    void largeDbBranchPageSearch() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("large.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket images = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images");
            assertNotNull(images);

            for (int i : new int[] { 0, 50, 99, 150, 199 }) {
                String name = String.format("image-%03d", i);
                Bucket image = images.getBucket(name.getBytes());
                assertNotNull(image, name + " should exist");
                Bucket target = image.getBucket("target");
                assertNotNull(target);
                String expectedDigest = String.format("sha256:%064d", i);
                assertEquals(expectedDigest, target.get("digest"));
            }
        }
    }

    @Test
    void largeDbCursorIteration() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("large.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket images = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images");
            int count = 0;
            Cursor cursor = images.cursor();
            for (Cursor.Entry e = cursor.first(); e != null; e = cursor.next()) {
                assertTrue(e.isBucket());
                count++;
            }
            assertEquals(200, count);
        }
    }

    @Test
    void largeDbCursorSeekAndNext() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("large.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket images = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images");
            Cursor cursor = images.cursor();
            Cursor.Entry e = cursor.seek("image-050".getBytes());
            assertNotNull(e);
            assertEquals("image-050", new String(e.key()));

            e = cursor.next();
            assertNotNull(e);
            assertEquals("image-051", new String(e.key()));
        }
    }

    @Test
    void largeDbCursorSeekAndPrev() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("large.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket images = tx.getRootBucket()
                    .getBucket("v1")
                    .getBucket("moby")
                    .getBucket("images");
            Cursor cursor = images.cursor();
            Cursor.Entry e = cursor.seek("image-050".getBytes());
            assertNotNull(e);
            assertEquals("image-050", new String(e.key()));

            e = cursor.prev();
            assertNotNull(e);
            assertEquals("image-049", new String(e.key()));
        }
    }

    @Test
    void invalidMagicThrowsOnOpen(@TempDir Path tempDir) throws Exception {
        Path copy = tempDir.resolve("invalid-magic.db");
        Files.copy(fixture("meta.db"), copy);
        try (RandomAccessFile raf = new RandomAccessFile(copy.toFile(), "rw")) {
            // meta.magic at file offset pageHeaderSize + 0
            raf.seek(16);
            raf.writeInt(0xDEADBEEF);
        }
        assertThrows(BboltException.class, () -> BboltDB.open(copy));
    }

    @Test
    void invalidChecksumThrowsOnOpen(@TempDir Path tempDir) throws Exception {
        Path copy = tempDir.resolve("invalid-checksum.db");
        Files.copy(fixture("meta.db"), copy);
        try (RandomAccessFile raf = new RandomAccessFile(copy.toFile(), "rw")) {
            // meta.pgid at file offset pageHeaderSize + 40 = 56
            raf.seek(56);
            raf.write(0x42);
        }
        assertThrows(BboltException.class, () -> BboltDB.open(copy));
    }

    @Test
    void openAndCloseMultipleTimes() throws Exception {
        Path path = fixture("meta.db");
        for (int i = 0; i < 3; i++) {
            try (BboltDB db = BboltDB.open(path);
                 ReadOnlyTransaction tx = db.beginReadOnly()) {
                assertEquals(Meta.MAGIC, tx.meta().magic());
            }
        }
    }

    @Test
    void rootBucketIsNotNull() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("meta.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket root = tx.getRootBucket();
            assertNotNull(root);
            assertTrue(root.rootPage() > 0 || root.inlinePage() != null);
        }
    }

    @Test
    void forEachBucketCollectsNames() throws Exception {
        try (BboltDB db = BboltDB.open(fixture("meta.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            List<String> names = new ArrayList<>();
            tx.getRootBucket().forEachBucket(b -> names.add(b.toString()));
            assertEquals(1, names.size());
        }
    }
}
