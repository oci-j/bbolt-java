package com.xenoamess.bbolt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

public class Bucket {

    private final ReadOnlyTransaction tx;
    private final long rootPage;
    private final Page inlinePage;
    private final long sequence;

    Bucket(ReadOnlyTransaction tx, long rootPage, Page inlinePage, long sequence) {
        this.tx = tx;
        this.rootPage = rootPage;
        this.inlinePage = inlinePage;
        this.sequence = sequence;
    }

    static Bucket openBucket(ReadOnlyTransaction tx, byte[] value) {
        ByteBuffer bb = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        InBucket inBucket = new InBucket(bb, 0);
        Page inline = null;
        if (inBucket.rootPage() == 0 && value.length > 16) {
            byte[] pageData = Arrays.copyOfRange(value, 16, value.length);
            inline = new Page(
                    ByteBuffer.wrap(pageData).order(ByteOrder.LITTLE_ENDIAN),
                    0
            );
        }
        return new Bucket(tx, inBucket.rootPage(), inline, inBucket.sequence());
    }

    long rootPage() {
        return rootPage;
    }

    Page inlinePage() {
        return inlinePage;
    }

    ReadOnlyTransaction transaction() {
        return tx;
    }

    public long sequence() {
        return sequence;
    }

    public Cursor cursor() {
        return new Cursor(this);
    }

    public byte[] get(byte[] key) {
        Cursor.Entry entry = cursor().seek(key);
        if (entry == null || entry.isBucket() || !Arrays.equals(entry.key(), key)) {
            return null;
        }
        return entry.value();
    }

    public String get(String key) {
        byte[] value = get(key.getBytes(StandardCharsets.UTF_8));
        if (value == null) {
            return null;
        }
        return new String(value, StandardCharsets.UTF_8);
    }

    public Bucket getBucket(String name) {
        return getBucket(name.getBytes(StandardCharsets.UTF_8));
    }

    public Bucket getBucket(byte[] name) {
        Cursor.Entry entry = cursor().seek(name);
        if (entry == null || !entry.isBucket() || !Arrays.equals(entry.key(), name)) {
            return null;
        }
        return openBucket(tx, entry.value());
    }

    public void forEach(Consumer<Cursor.Entry> consumer) {
        Cursor cursor = cursor();
        for (Cursor.Entry entry = cursor.first(); entry != null; entry = cursor.next()) {
            consumer.accept(entry);
        }
    }

    public void forEachBucket(Consumer<Bucket> consumer) {
        Cursor cursor = cursor();
        for (Cursor.Entry entry = cursor.first(); entry != null; entry = cursor.next()) {
            if (entry.isBucket()) {
                consumer.accept(openBucket(tx, entry.value()));
            }
        }
    }

    @Override
    public String toString() {
        return "Bucket{rootPage=" + rootPage + ", sequence=" + sequence + '}';
    }
}
