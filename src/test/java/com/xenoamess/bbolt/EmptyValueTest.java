package com.xenoamess.bbolt;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmptyValueTest {

    private static Path fixture() throws Exception {
        return Paths.get(EmptyValueTest.class.getResource("/empty-value.db").toURI());
    }

    @Test
    void emptyValueReturnsEmptyArrayNotNull() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket markers = tx.getRootBucket().getBucket("markers");
            assertNotNull(markers);

            byte[] a = markers.get("a".getBytes());
            assertNotNull(a, "key with nil value must be distinguishable from a missing key");
            assertEquals(0, a.length);

            byte[] b = markers.get("b".getBytes());
            assertNotNull(b);
            assertEquals(0, b.length);
        }
    }

    @Test
    void normalKeyNextToEmptyValuesIsUnaffected() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket markers = tx.getRootBucket().getBucket("markers");
            assertEquals("x", markers.get("c"));
        }
    }

    @Test
    void missingKeyStillReturnsNull() throws Exception {
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket markers = tx.getRootBucket().getBucket("markers");
            assertNull(markers.get("missing".getBytes()));
            assertNull(markers.get("missing"), "String overload should also return null");
        }
    }
}
