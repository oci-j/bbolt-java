package com.xenoamess.bbolt;

import java.nio.ByteBuffer;

public class BranchPageElement {

    private static final int OFFSET_POS = 0;
    private static final int OFFSET_KSIZE = 4;
    private static final int OFFSET_PGID = 8;

    private final ByteBuffer data;
    private final int offset;

    public BranchPageElement(ByteBuffer data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    public int pos() {
        return data.getInt(offset + OFFSET_POS);
    }

    public int ksize() {
        return data.getInt(offset + OFFSET_KSIZE);
    }

    public long pgid() {
        return data.getLong(offset + OFFSET_PGID);
    }

    public byte[] key() {
        int start = offset + pos();
        int length = ksize();
        if (length == 0) {
            return new byte[0];
        }
        byte[] result = new byte[length];
        data.duplicate().position(start).get(result, 0, length);
        return result;
    }

    @Override
    public String toString() {
        return "BranchPageElement{"
                + "pgid=" + pgid()
                + ", key=" + new String(key())
                + '}';
    }
}
