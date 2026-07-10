package com.xenoamess.bbolt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Cursor {

    private final Bucket bucket;
    private final List<ElemRef> stack = new ArrayList<>();

    Cursor(Bucket bucket) {
        this.bucket = bucket;
    }

    public Bucket bucket() {
        return bucket;
    }

    public Entry first() {
        stack.clear();
        Page root = pageNode(bucket.rootPage());
        stack.add(new ElemRef(root, 0));
        goToFirstElementOnTheStack();
        if (top().count() == 0) {
            return next();
        }
        return keyValue();
    }

    public Entry last() {
        stack.clear();
        Page root = pageNode(bucket.rootPage());
        ElemRef ref = new ElemRef(root, Math.max(0, root.count() - 1));
        stack.add(ref);
        while (!ref.isLeaf()) {
            BranchPageElement elem = ref.page.branchElement(ref.index);
            Page child = pageNode(elem.pgid());
            ref = new ElemRef(child, Math.max(0, child.count() - 1));
            stack.add(ref);
        }
        return keyValue();
    }

    public Entry next() {
        while (true) {
            int i = stack.size() - 1;
            while (i >= 0) {
                ElemRef ref = stack.get(i);
                if (ref.index < ref.count() - 1) {
                    ref.index++;
                    break;
                }
                i--;
            }
            if (i < 0) {
                return null;
            }
            stack.subList(i + 1, stack.size()).clear();
            goToFirstElementOnTheStack();
            if (top().count() == 0) {
                continue;
            }
            return keyValue();
        }
    }

    public Entry prev() {
        while (true) {
            int i = stack.size() - 1;
            while (i >= 0) {
                ElemRef ref = stack.get(i);
                if (ref.index > 0) {
                    ref.index--;
                    break;
                }
                i--;
            }
            if (i < 0) {
                return null;
            }
            stack.subList(i + 1, stack.size()).clear();
            while (!top().isLeaf()) {
                ElemRef ref = top();
                BranchPageElement elem = ref.page.branchElement(ref.index);
                Page child = pageNode(elem.pgid());
                stack.add(new ElemRef(child, Math.max(0, child.count() - 1)));
            }
            return keyValue();
        }
    }

    public Entry seek(byte[] key) {
        stack.clear();
        search(key, bucket.rootPage());
        return keyValue();
    }

    private void search(byte[] key, long pgid) {
        Page page = pageNode(pgid);
        ElemRef ref = new ElemRef(page, 0);
        stack.add(ref);
        if (ref.isLeaf()) {
            nsearch(key);
            return;
        }
        searchPage(key, page);
    }

    private void searchPage(byte[] key, Page page) {
        List<BranchPageElement> elems = page.branchElements();
        int index = lowerBoundBranch(elems, key);
        boolean exact = index < elems.size()
                && Arrays.compareUnsigned(elems.get(index).key(), key) == 0;
        if (!exact && index > 0) {
            index--;
        }
        stack.get(stack.size() - 1).index = index;
        search(key, elems.get(index).pgid());
    }

    private void nsearch(byte[] key) {
        Page page = top().page;
        List<LeafPageElement> elems = page.leafElements();
        int index = lowerBoundLeaf(elems, key);
        top().index = index;
    }

    private int lowerBoundLeaf(List<LeafPageElement> elems, byte[] key) {
        int lo = 0;
        int hi = elems.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = Arrays.compareUnsigned(elems.get(mid).key(), key);
            if (cmp < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private int lowerBoundBranch(List<BranchPageElement> elems, byte[] key) {
        int lo = 0;
        int hi = elems.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = Arrays.compareUnsigned(elems.get(mid).key(), key);
            if (cmp < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private void goToFirstElementOnTheStack() {
        while (!top().isLeaf()) {
            ElemRef ref = top();
            BranchPageElement elem = ref.page.branchElement(ref.index);
            Page child = pageNode(elem.pgid());
            stack.add(new ElemRef(child, 0));
        }
    }

    private Page pageNode(long id) {
        if (bucket.rootPage() == 0) {
            if (id != 0) {
                throw new BboltException("inline bucket accessed with non-zero page id: " + id);
            }
            return bucket.inlinePage();
        }
        return bucket.transaction().readPage(id);
    }

    private ElemRef top() {
        return stack.get(stack.size() - 1);
    }

    private Entry keyValue() {
        if (stack.isEmpty()) {
            return null;
        }
        ElemRef ref = top();
        if (ref.count() == 0 || ref.index >= ref.count()) {
            return null;
        }
        if (!ref.isLeaf()) {
            return null;
        }
        LeafPageElement elem = ref.page.leafElement(ref.index);
        return new Entry(elem.key(), elem.value(), elem.flags());
    }

    private static final class ElemRef {
        final Page page;
        int index;

        ElemRef(Page page, int index) {
            this.page = page;
            this.index = index;
        }

        boolean isLeaf() {
            return page.isLeafPage();
        }

        int count() {
            return page.count();
        }
    }

    public static final class Entry {
        private final byte[] key;
        private final byte[] value;
        private final int flags;

        Entry(byte[] key, byte[] value, int flags) {
            this.key = key;
            this.value = value;
            this.flags = flags;
        }

        public byte[] key() {
            return key;
        }

        public byte[] value() {
            return value;
        }

        public int flags() {
            return flags;
        }

        public boolean isBucket() {
            return (flags & Page.BUCKET_LEAF_FLAG) != 0;
        }

        @Override
        public String toString() {
            return "Entry{"
                    + "key=" + new String(key)
                    + ", valueLen=" + (value == null ? 0 : value.length)
                    + ", flags=" + flags
                    + '}';
        }
    }
}
