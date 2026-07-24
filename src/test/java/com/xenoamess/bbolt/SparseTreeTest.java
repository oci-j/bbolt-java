package com.xenoamess.bbolt;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SparseTreeTest {

    private static final int KEY_COUNT = 500;

    private static Path fixture() throws Exception {
        return Paths.get(SparseTreeTest.class.getResource("/sparse.db").toURI());
    }

    private static String key(int i) {
        return String.format("key-%04d", i);
    }

    @Test
    void forwardIterationYieldsAllKeysInOrder() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket wide = tx.getRootBucket().getBucket("wide");
            assertNotNull(wide);
            List<String> keys = new ArrayList<>();
            wide.forEach(entry -> keys.add(new String(entry.key())));
            assertEquals(KEY_COUNT, keys.size());
            for (int i = 0; i < KEY_COUNT; i++) {
                assertEquals(key(i), keys.get(i));
            }
        }
    }

    @Test
    void lastDescendsBranchPagesToFinalLeaf() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket wide = tx.getRootBucket().getBucket("wide");
            Cursor.Entry last = wide.cursor().last();
            assertNotNull(last);
            assertEquals(key(KEY_COUNT - 1), new String(last.key()));
        }
    }

    @Test
    void prevCrossesLeafPageBoundaries() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket wide = tx.getRootBucket().getBucket("wide");
            Cursor cursor = wide.cursor();
            Cursor.Entry e = cursor.seek(key(250).getBytes());
            assertNotNull(e);
            assertEquals(key(250), new String(e.key()));

            // Walk backwards across multiple leaf pages: the sequence must
            // continue exactly with 249, 248, ... down to 0 without gaps.
            for (int i = 249; i >= 0; i--) {
                e = cursor.prev();
                assertNotNull(e, "prev() lost track at " + key(i));
                assertEquals(key(i), new String(e.key()));
            }
            assertNull(cursor.prev(), "prev() past the first key should be null");
        }
    }

    @Test
    void seekThenNextAcrossLeafBoundary() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket wide = tx.getRootBucket().getBucket("wide");
            Cursor cursor = wide.cursor();
            Cursor.Entry e = cursor.seek(key(498).getBytes());
            assertEquals(key(498), new String(e.key()));
            e = cursor.next();
            assertNotNull(e);
            assertEquals(key(499), new String(e.key()));
            assertNull(cursor.next());
        }
    }
}
