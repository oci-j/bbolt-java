package com.xenoamess.bbolt;

public class ReadOnlyTransaction implements AutoCloseable {

    private final BboltDB db;
    private final Meta meta;
    private final Bucket root;

    ReadOnlyTransaction(BboltDB db, Meta meta) {
        this.db = db;
        this.meta = meta;
        InBucket rootInBucket = meta.rootBucket();
        this.root = new Bucket(
                this,
                rootInBucket.rootPage(),
                null,
                rootInBucket.sequence()
        );
    }

    public BboltDB db() {
        return db;
    }

    public Meta meta() {
        return meta;
    }

    public Bucket getRootBucket() {
        return root;
    }

    Page readPage(long id) {
        return db.readPage(id);
    }

    @Override
    public void close() {
        // no-op for read-only view
    }
}
