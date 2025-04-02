package io.wrtn.engine.lucene.store.s3.lock;

import org.apache.lucene.store.Lock;

public class NoopLock extends Lock {
    @Override
    public void ensureValid() {
    }

    @Override
    public void close() {
    }
}
