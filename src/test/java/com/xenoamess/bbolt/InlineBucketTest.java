package com.xenoamess.bbolt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineBucketTest {

    private static Path fixture() throws Exception {
        return Paths.get(InlineBucketTest.class.getResource("/empty.db").toURI());
    }

    private static byte[] inlineValue(int pageFlags, int count, byte[] body) {
        ByteBuffer bb = ByteBuffer.allocate(16 + Page.HEADER_SIZE + body.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        bb.putLong(0); // inbucket.root = 0 -> inline bucket
        bb.putLong(0); // inbucket.sequence
        bb.putLong(0); // page.id
        bb.putShort((short) pageFlags);
        bb.putShort((short) count);
        bb.putInt(0); // page.overflow
        bb.put(body);
        return bb.array();
    }

    @Test
    void emptyInlineBucketYieldsNoEntries() throws Exception {
        byte[] value = inlineValue(Page.LEAF_PAGE_FLAG, 0, new byte[0]);
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket bucket = Bucket.openBucket(tx, value);
            assertEquals(0, bucket.rootPage());
            assertNotNull(bucket.inlinePage());
            Cursor cursor = bucket.cursor();
            assertNull(cursor.first());
            assertNull(cursor.last());
        }
    }

    @Test
    void inlineBucketWithEntriesIsReadable() throws Exception {
        byte[] key = "k".getBytes();
        byte[] val = "v".getBytes();
        ByteBuffer body = ByteBuffer.allocate(Page.LEAF_ELEMENT_SIZE + key.length + val.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        body.putInt(0); // flags: plain kv
        body.putInt(Page.LEAF_ELEMENT_SIZE); // pos, relative to element start
        body.putInt(key.length);
        body.putInt(val.length);
        body.put(key);
        body.put(val);

        byte[] value = inlineValue(Page.LEAF_PAGE_FLAG, 1, body.array());
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket bucket = Bucket.openBucket(tx, value);
            assertEquals("v", bucket.get("k"));
            assertNull(bucket.get("missing"));
        }
    }

    @Test
    void degenerateInlineBucketWithoutPageDataThrowsPreciseError() throws Exception {
        byte[] value = new byte[16]; // inbucket header only, no inline page
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket bucket = Bucket.openBucket(tx, value);
            assertNull(bucket.inlinePage());
            BboltException e = assertThrows(BboltException.class, () -> bucket.cursor().first());
            assertTrue(e.getMessage().contains("no inline page data"), e.getMessage());
        }
    }

    @Test
    void malformedInlineBucketWithBranchPageThrowsPreciseError() throws Exception {
        ByteBuffer body = ByteBuffer.allocate(Page.BRANCH_ELEMENT_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);
        body.putInt(Page.BRANCH_ELEMENT_SIZE); // pos
        body.putInt(0); // ksize
        body.putLong(7); // child pgid, illegal for an inline bucket

        byte[] value = inlineValue(Page.BRANCH_PAGE_FLAG, 1, body.array());
        try (BboltDB db = BboltDB.open(fixture());
             ReadOnlyTransaction tx = db.beginReadOnly()) {
            Bucket bucket = Bucket.openBucket(tx, value);
            BboltException e = assertThrows(BboltException.class, () -> bucket.cursor().first());
            assertTrue(e.getMessage().contains("inline bucket"), e.getMessage());
        }
    }

    @Test
    void pageTypeLabelsMatchFlags() {
        assertEquals("branch", pageWithFlags(Page.BRANCH_PAGE_FLAG).type());
        assertEquals("leaf", pageWithFlags(Page.LEAF_PAGE_FLAG).type());
        assertEquals("meta", pageWithFlags(Page.META_PAGE_FLAG).type());
        assertEquals("freelist", pageWithFlags(Page.FREELIST_PAGE_FLAG).type());
        assertEquals("unknown", pageWithFlags(0x99).type());

        Page branch = pageWithFlags(Page.BRANCH_PAGE_FLAG);
        assertTrue(branch.isBranchPage());
        assertEquals(42L, branch.id());
    }

    private static Page pageWithFlags(int flags) {
        ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort(8, (short) flags);
        return new Page(bb, 42);
    }
}
