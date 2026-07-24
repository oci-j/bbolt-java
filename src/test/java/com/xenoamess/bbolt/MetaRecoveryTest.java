package com.xenoamess.bbolt;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaRecoveryTest {

    private static final int PAGE_SIZE = 4096;

    private static Path fixture(String name) throws Exception {
        return Paths.get(MetaRecoveryTest.class.getResource("/" + name).toURI());
    }

    private static Path copyFixture(String name, Path tempDir, String target) throws Exception {
        Path copy = tempDir.resolve(target);
        Files.copy(fixture(name), copy);
        return copy;
    }

    private static void corruptMagic(Path file, long pageOffset) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.seek(pageOffset + 16);
            raf.writeInt(0xDEADBEEF);
        }
    }

    private static void corruptChecksum(Path file, long pageOffset) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.seek(pageOffset + 56);
            int original = raf.read();
            raf.seek(pageOffset + 56);
            raf.write(original ^ 0xFF);
        }
    }

    @Test
    void meta0DamagedMagicOpensViaMeta1(@TempDir Path tempDir) throws Exception {
        Path copy = copyFixture("pagesize-64k.db", tempDir, "damaged-magic.db");
        corruptMagic(copy, 0);
        try (BboltDB db = BboltDB.open(copy);
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            assertEquals(2L, tx.meta().txid(), "should recover with meta page 1");
            assertEquals("world-64k-page", tx.getRootBucket().get("hello"));
        }
    }

    @Test
    void recoveredMetaYieldsValidOlderSnapshot(@TempDir Path tempDir) throws Exception {
        // In meta.db the meta page 0 (txid 2) holds the data; meta page 1
        // (txid 1) is the previous commit whose root bucket is still empty.
        Path copy = copyFixture("meta.db", tempDir, "damaged-magic2.db");
        corruptMagic(copy, 0);
        try (BboltDB db = BboltDB.open(copy);
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            assertEquals(1L, tx.meta().txid());
            assertNull(tx.getRootBucket().cursor().first(),
                    "older snapshot has an empty root bucket");
        }
    }

    @Test
    void meta0DamagedChecksumOpensViaMeta1(@TempDir Path tempDir) throws Exception {
        Path copy = copyFixture("meta.db", tempDir, "damaged-checksum.db");
        corruptChecksum(copy, 0);
        try (BboltDB db = BboltDB.open(copy);
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            assertEquals(1L, tx.meta().txid());
        }
    }

    @Test
    void newerTxidWinsInBothDirections(@TempDir Path tempDir) throws Exception {
        try (BboltDB db = BboltDB.open(fixture("meta.db"));
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            assertEquals(2L, tx.meta().txid(), "meta0 (txid 2) should win originally");
        }

        Path swapped = tempDir.resolve("swapped.db");
        byte[] data = Files.readAllBytes(fixture("meta.db"));
        byte[] page0 = new byte[PAGE_SIZE];
        System.arraycopy(data, 0, page0, 0, PAGE_SIZE);
        System.arraycopy(data, PAGE_SIZE, data, 0, PAGE_SIZE);
        System.arraycopy(page0, 0, data, PAGE_SIZE, PAGE_SIZE);
        Files.write(swapped, data);

        try (BboltDB db = BboltDB.open(swapped);
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            assertEquals(2L, tx.meta().txid(), "meta1 (txid 2) should win after swap");
        }
    }

    @Test
    void bothMetasDamagedMagicThrowsPreciseError(@TempDir Path tempDir) throws Exception {
        Path copy = copyFixture("meta.db", tempDir, "both-magic.db");
        corruptMagic(copy, 0);
        corruptMagic(copy, PAGE_SIZE);
        BboltException e = assertThrows(BboltException.class, () -> BboltDB.open(copy));
        assertTrue(e.getMessage().contains("invalid bbolt magic"), e.getMessage());
    }

    @Test
    void bothMetasDamagedChecksumThrowsPreciseError(@TempDir Path tempDir) throws Exception {
        Path copy = copyFixture("meta.db", tempDir, "both-checksum.db");
        corruptChecksum(copy, 0);
        corruptChecksum(copy, PAGE_SIZE);
        BboltException e = assertThrows(BboltException.class, () -> BboltDB.open(copy));
        assertTrue(e.getMessage().contains("checksum"), e.getMessage());
    }

    @Test
    void unsupportedVersionThrowsPreciseError(@TempDir Path tempDir) throws Exception {
        Path copy = copyFixture("meta.db", tempDir, "bad-version.db");
        try (RandomAccessFile raf = new RandomAccessFile(copy.toFile(), "rw")) {
            raf.seek(20);
            raf.writeInt(99);
            raf.seek(PAGE_SIZE + 20);
            raf.writeInt(99);
        }
        BboltException e = assertThrows(BboltException.class, () -> BboltDB.open(copy));
        assertTrue(e.getMessage().contains("unsupported bbolt version"), e.getMessage());
    }

    @Test
    void truncatedFileThrowsBboltException(@TempDir Path tempDir) throws Exception {
        Path tiny = tempDir.resolve("tiny.db");
        byte[] data = Files.readAllBytes(fixture("meta.db"));
        byte[] truncated = new byte[1000];
        System.arraycopy(data, 0, truncated, 0, truncated.length);
        Files.write(tiny, truncated);
        BboltException e = assertThrows(BboltException.class, () -> BboltDB.open(tiny));
        assertTrue(e.getMessage().contains("too small"), e.getMessage());
    }
}
