package io.wrtn.engine.lucene.store.s3.cache.fs;

import io.wrtn.engine.lucene.store.s3.storage.s3.S3Storage;
import java.nio.file.Files;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FSCache {

    private final FSDirectory dir;
    private final S3Storage storage;

    public FSCache(FSCacheConfig config, S3Storage storage) throws IOException {
        this.dir = FSDirectory.open(Paths.get(config.dir()));
        this.storage = storage;
    }

    public IndexInput openInput(String name, IOContext context)
        throws IOException {
        Path path = dir.getDirectory().resolve(name);
        if (!Files.exists(path) || (Files.size(path) != storage.fileLength(name))) {
            storage.readToFile(name, path);
        }

        return dir.openInput(name, context);
    }

    public void close() throws IOException {
        dir.close();
    }

    public Boolean exists(String name) {
        return Files.exists(dir.getDirectory().resolve(name));
    }

    public void deleteFile(String name) throws IOException {
        dir.deleteFile(name);
    }
}