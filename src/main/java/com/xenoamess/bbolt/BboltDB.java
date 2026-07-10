package com.xenoamess.bbolt;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class BboltDB implements AutoCloseable {

    private final Path path;
    private final RandomAccessFile file;
    private final int pageSize;
    private final Meta meta;

    private BboltDB(Path path, RandomAccessFile file, int pageSize, Meta meta) {
        this.path = path;
        this.file = file;
        this.pageSize = pageSize;
        this.meta = meta;
    }

    public static BboltDB open(Path path) throws IOException {
        RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
        try {
            // Read the first 4 KB, which is enough to parse meta page 0 and obtain pageSize.
            byte[] initial = new byte[4096];
            file.readFully(initial);
            ByteBuffer bb0 = ByteBuffer.wrap(initial).order(ByteOrder.LITTLE_ENDIAN);
            Page p0 = new Page(bb0, 0);
            Meta m0 = p0.meta();
            if (!m0.isValid()) {
                throw new BboltException("invalid meta page 0 at " + path);
            }

            int pageSize = m0.pageSize();

            byte[] buf1 = new byte[pageSize];
            file.seek(pageSize);
            file.readFully(buf1);
            ByteBuffer bb1 = ByteBuffer.wrap(buf1).order(ByteOrder.LITTLE_ENDIAN);
            Page p1 = new Page(bb1, 1);
            Meta m1 = p1.meta();

            Meta meta = chooseMeta(m0, m1);
            return new BboltDB(path, file, pageSize, meta);
        } catch (Exception e) {
            file.close();
            throw e;
        }
    }

    private static Meta chooseMeta(Meta m0, Meta m1) {
        boolean valid0 = m0.isValid();
        boolean valid1 = m1.isValid();
        if (valid0 && valid1) {
            return m0.txid() >= m1.txid() ? m0 : m1;
        }
        if (valid0) {
            return m0;
        }
        if (valid1) {
            return m1;
        }
        throw new BboltException("both meta pages are invalid");
    }

    public Path path() {
        return path;
    }

    public int pageSize() {
        return pageSize;
    }

    public Meta meta() {
        return meta;
    }

    public ReadOnlyTransaction beginReadOnly() {
        return new ReadOnlyTransaction(this, meta);
    }

    Page readPage(long id) {
        try {
            long offset = id * pageSize;
            byte[] header = new byte[pageSize];
            file.seek(offset);
            file.readFully(header);
            ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            Page page = new Page(bb, id);
            long overflow = page.overflow();
            if (overflow == 0) {
                return page;
            }
            int totalSize = pageSize * ((int) overflow + 1);
            byte[] full = new byte[totalSize];
            System.arraycopy(header, 0, full, 0, pageSize);
            file.readFully(full, pageSize, totalSize - pageSize);
            ByteBuffer fullBb = ByteBuffer.wrap(full).order(ByteOrder.LITTLE_ENDIAN);
            return new Page(fullBb, id);
        } catch (IOException e) {
            throw new BboltException("failed to read page " + id, e);
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
