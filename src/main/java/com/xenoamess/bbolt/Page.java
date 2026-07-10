package com.xenoamess.bbolt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Page {

    public static final int HEADER_SIZE = 16;
    public static final int LEAF_ELEMENT_SIZE = 16;
    public static final int BRANCH_ELEMENT_SIZE = 16;

    public static final int BRANCH_PAGE_FLAG = 0x01;
    public static final int LEAF_PAGE_FLAG = 0x02;
    public static final int META_PAGE_FLAG = 0x04;
    public static final int FREELIST_PAGE_FLAG = 0x10;

    public static final int BUCKET_LEAF_FLAG = 0x01;

    private final ByteBuffer data;
    private final long id;

    public Page(ByteBuffer data, long id) {
        this.data = data;
        this.id = id;
    }

    public long id() {
        return id;
    }

    public int flags() {
        return data.getShort(8) & 0xFFFF;
    }

    public int count() {
        return Short.toUnsignedInt(data.getShort(10));
    }

    public long overflow() {
        return Integer.toUnsignedLong(data.getInt(12));
    }

    public boolean isBranchPage() {
        return flags() == BRANCH_PAGE_FLAG;
    }

    public boolean isLeafPage() {
        return flags() == LEAF_PAGE_FLAG;
    }

    public boolean isMetaPage() {
        return flags() == META_PAGE_FLAG;
    }

    public boolean isFreelistPage() {
        return flags() == FREELIST_PAGE_FLAG;
    }

    public String type() {
        if (isBranchPage()) {
            return "branch";
        } else if (isLeafPage()) {
            return "leaf";
        } else if (isMetaPage()) {
            return "meta";
        } else if (isFreelistPage()) {
            return "freelist";
        }
        return "unknown";
    }

    public Meta meta() {
        return new Meta(data, HEADER_SIZE);
    }

    public LeafPageElement leafElement(int index) {
        return new LeafPageElement(data, HEADER_SIZE + (index * LEAF_ELEMENT_SIZE));
    }

    public List<LeafPageElement> leafElements() {
        int count = count();
        List<LeafPageElement> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(leafElement(i));
        }
        return result;
    }

    public BranchPageElement branchElement(int index) {
        return new BranchPageElement(data, HEADER_SIZE + (index * BRANCH_ELEMENT_SIZE));
    }

    public List<BranchPageElement> branchElements() {
        int count = count();
        List<BranchPageElement> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(branchElement(i));
        }
        return result;
    }

    ByteBuffer data() {
        return data;
    }

    @Override
    public String toString() {
        return "Page{"
                + "id=" + id
                + ", type=" + type()
                + ", count=" + count()
                + ", overflow=" + overflow()
                + '}';
    }
}
