package com.xenoamess.bbolt;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class LeafPageElement {

    private static final int OFFSET_FLAGS = 0;
    private static final int OFFSET_POS = 4;
    private static final int OFFSET_KSIZE = 8;
    private static final int OFFSET_VSIZE = 12;

    private final ByteBuffer data;
    private final int offset;

    public LeafPageElement(ByteBuffer data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    public int flags() {
        return data.getInt(offset + OFFSET_FLAGS);
    }

    public int pos() {
        return data.getInt(offset + OFFSET_POS);
    }

    public int ksize() {
        return data.getInt(offset + OFFSET_KSIZE);
    }

    public int vsize() {
        return data.getInt(offset + OFFSET_VSIZE);
    }

    public byte[] key() {
        int start = offset + pos();
        return readBytes(start, ksize());
    }

    public byte[] value() {
        int start = offset + pos() + ksize();
        return readBytes(start, vsize());
    }

    public boolean isBucketEntry() {
        return (flags() & Page.BUCKET_LEAF_FLAG) != 0;
    }

    private byte[] readBytes(int start, int length) {
        if (length == 0) {
            return new byte[0];
        }
        byte[] result = new byte[length];
        data.duplicate().position(start).get(result, 0, length);
        return result;
    }

    @Override
    public String toString() {
        return "LeafPageElement{"
                + "flags=" + flags()
                + ", key=" + new String(key())
                + ", valueLen=" + vsize()
                + '}';
    }
}
