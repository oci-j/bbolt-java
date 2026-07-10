package com.xenoamess.bbolt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class InBucket {

    private static final int OFFSET_ROOT = 0;
    private static final int OFFSET_SEQUENCE = 8;

    private final long rootPage;
    private final long sequence;

    public InBucket(long rootPage, long sequence) {
        this.rootPage = rootPage;
        this.sequence = sequence;
    }

    public InBucket(ByteBuffer data, int offset) {
        this(data.getLong(offset + OFFSET_ROOT), data.getLong(offset + OFFSET_SEQUENCE));
    }

    public long rootPage() {
        return rootPage;
    }

    public long sequence() {
        return sequence;
    }

    @Override
    public String toString() {
        return "<pgid=" + rootPage + ",seq=" + sequence + ">";
    }
}
