package com.xenoamess.bbolt;

import java.nio.ByteBuffer;

public class Meta {

    // magic + version + pageSize + flags = 16 bytes
    private static final int OFFSET_MAGIC = 0;
    private static final int OFFSET_VERSION = 4;
    private static final int OFFSET_PAGE_SIZE = 8;
    private static final int OFFSET_FLAGS = 12;
    private static final int OFFSET_ROOT = 16;
    private static final int OFFSET_FREELIST = 32;
    private static final int OFFSET_PGID = 40;
    private static final int OFFSET_TXID = 48;
    private static final int OFFSET_CHECKSUM = 56;

    private static final int CHECKSUM_LENGTH = OFFSET_CHECKSUM;

    public static final int MAGIC = 0xED0CDAED;
    public static final int VERSION = 2;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final ByteBuffer data;
    private final int offset;

    public Meta(ByteBuffer data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    public int magic() {
        return data.getInt(offset + OFFSET_MAGIC);
    }

    public int version() {
        return data.getInt(offset + OFFSET_VERSION);
    }

    public int pageSize() {
        return data.getInt(offset + OFFSET_PAGE_SIZE);
    }

    public int flags() {
        return data.getInt(offset + OFFSET_FLAGS);
    }

    public InBucket rootBucket() {
        return new InBucket(data, offset + OFFSET_ROOT);
    }

    public long freelist() {
        return data.getLong(offset + OFFSET_FREELIST);
    }

    public long pgid() {
        return data.getLong(offset + OFFSET_PGID);
    }

    public long txid() {
        return data.getLong(offset + OFFSET_TXID);
    }

    public long checksum() {
        return data.getLong(offset + OFFSET_CHECKSUM);
    }

    public boolean isValid() {
        if (magic() != MAGIC) {
            return false;
        }
        if (version() != VERSION) {
            return false;
        }
        return checksum() == sum64();
    }

    public void validate() {
        if (magic() != MAGIC) {
            throw new BboltException("invalid bbolt magic: " + Integer.toHexString(magic()));
        }
        if (version() != VERSION) {
            throw new BboltException("unsupported bbolt version: " + version());
        }
        if (checksum() != sum64()) {
            throw new BboltException("meta checksum mismatch");
        }
    }

    long sum64() {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < CHECKSUM_LENGTH; i++) {
            int b = data.get(offset + i) & 0xFF;
            hash ^= b;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    @Override
    public String toString() {
        return "Meta{"
                + "version=" + version()
                + ", pageSize=" + pageSize()
                + ", flags=" + flags()
                + ", root=" + rootBucket()
                + ", freelist=" + freelist()
                + ", pgid=" + pgid()
                + ", txid=" + txid()
                + ", checksum=" + Long.toHexString(checksum())
                + '}';
    }
}
