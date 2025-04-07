package io.wrtn.engine.lucene.store;

import io.wrtn.engine.lucene.store.s3.buffer.fs.FSBuffer;
import io.wrtn.engine.lucene.store.s3.cache.fs.FSCache;
import io.wrtn.engine.lucene.store.s3.storage.s3.S3Storage;
import java.io.IOException;

import io.wrtn.engine.lucene.store.s3.S3Directory;
import java.nio.file.Path;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public final class DirectoryBuilder {

    public static S3Directory build(
        final S3Storage storage,
        final FSCache cache,
        final FSBuffer buffer) throws IOException {

        return new S3Directory(storage, cache, buffer);
    }

    public static Directory build(final String fsPath) throws IOException {
        return FSDirectory.open((Path.of(fsPath)));
    }
}
