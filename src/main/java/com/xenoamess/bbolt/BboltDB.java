package com.xenoamess.bbolt;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

public class BboltDB implements AutoCloseable {

    private static final int[] CANDIDATE_PAGE_SIZES = { 4096, 8192, 16384, 32768, 65536 };

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
            Meta meta = readMeta(file, path);
            return new BboltDB(path, file, meta.pageSize(), meta);
        } catch (Exception e) {
            file.close();
            throw e;
        }
    }

    private static Meta readMeta(RandomAccessFile file, Path path) throws IOException {
        // Read the first 4 KB, which is enough to parse meta page 0 and obtain pageSize.
        byte[] initial = new byte[4096];
        try {
            file.readFully(initial);
        } catch (EOFException e) {
            throw new BboltException("file is too small to be a bbolt database: " + path, e);
        }
        ByteBuffer bb0 = ByteBuffer.wrap(initial).order(ByteOrder.LITTLE_ENDIAN);
        Meta m0 = new Page(bb0, 0).meta();

        if (m0.isValid()) {
            Meta m1 = readMetaPage(file, m0.pageSize());
            return chooseMeta(m0, m1);
        }

        // Meta page 0 is damaged (e.g. torn write during a crash). bbolt writes
        // the two meta pages alternately, so meta page 1 may still be intact;
        // probe the common page sizes to locate and validate it.
        BboltException diagnosis = diagnosisOf(m0);
        for (int candidate : CANDIDATE_PAGE_SIZES) {
            Meta m1 = readMetaPage(file, candidate);
            if (m1 != null && m1.isValid()) {
                return m1;
            }
        }
        throw new BboltException("no valid meta page found at " + path + ": " + diagnosis.getMessage(), diagnosis);
    }

    private static Meta readMetaPage(RandomAccessFile file, int pageSize) throws IOException {
        byte[] buf = new byte[pageSize];
        try {
            file.seek(pageSize);
            file.readFully(buf);
        } catch (EOFException e) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        return new Page(bb, 1).meta();
    }

    private static BboltException diagnosisOf(Meta meta) {
        try {
            meta.validate();
        } catch (BboltException e) {
            return e;
        }
        return new BboltException("meta page is invalid");
    }

    private static Meta chooseMeta(Meta m0, Meta m1) {
        if (m1 != null && m1.isValid() && m1.txid() > m0.txid()) {
            return m1;
        }
        return m0;
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
