package com.xenoamess.bbolt;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OverflowValueTest {

    private static Path fixture() throws Exception {
        return Paths.get(OverflowValueTest.class.getResource("/overflow.db").toURI());
    }

    private static byte[] pattern(int size, int mod) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) (i % mod);
        }
        return b;
    }

    @Test
    void largeValuesSpanningOverflowPagesRoundTrip() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket data = tx.getRootBucket().getBucket("data");
            assertNotNull(data);
            assertArrayEquals(pattern(10240, 251), data.get("large-10k".getBytes()));
            assertArrayEquals(pattern(20480, 253), data.get("large-20k".getBytes()));
        }
    }

    @Test
    void smallValueNextToLargeValuesIsUnaffected() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket data = tx.getRootBucket().getBucket("data");
            assertEquals("ok", data.get("small"));
        }
    }

    @Test
    void nestedBucketLargeValueRoundTrip() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket nested = tx.getRootBucket().getBucket("nested");
            assertNotNull(nested);
            assertArrayEquals(pattern(8192, 241), nested.get("big".getBytes()));
        }
    }

    @Test
    void iterationOverBucketWithLargeValues() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket data = tx.getRootBucket().getBucket("data");
            List<String> keys = new ArrayList<>();
            data.forEach(entry -> keys.add(new String(entry.key())));
            assertEquals(List.of("large-10k", "large-20k", "small"), keys);
        }
    }
}
