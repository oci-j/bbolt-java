package com.xenoamess.bbolt;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PageSize64KTest {

    private static Path fixture() throws Exception {
        return Paths.get(PageSize64KTest.class.getResource("/pagesize-64k.db").toURI());
    }

    @Test
    void opensAndReads64KPageDatabase() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            assertEquals(65536, db.pageSize());
            assertEquals(2L, tx.meta().txid(), "meta1 (txid 2) should win over meta0 (txid 1)");
            assertEquals(0L, tx.meta().freelist());
            assertEquals(3L, tx.meta().pgid());
            assertEquals(0, tx.meta().flags());
            Bucket root = tx.getRootBucket();
            assertEquals("world-64k-page", root.get("hello"));
            assertEquals("value2", root.get("second"));
        }
    }

    @Test
    void exposesHandleAccessors() throws Exception {
        Path path = fixture();
        try (BboltDB db = BboltDB.open(path);
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            assertEquals(path, db.path());
            assertEquals(db, tx.db());
            assertEquals(db, tx.getRootBucket().transaction().db());
            assertEquals(0L, tx.getRootBucket().sequence());
        }
    }

    @Test
    void cursorTraversesSingleLeaf() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Cursor cursor = tx.getRootBucket().cursor();
            Cursor.Entry first = cursor.first();
            assertNotNull(first);
            assertEquals("hello", new String(first.key()));
            Cursor.Entry second = cursor.next();
            assertNotNull(second);
            assertEquals("second", new String(second.key()));
            assertNull(cursor.next());
        }
    }
}
