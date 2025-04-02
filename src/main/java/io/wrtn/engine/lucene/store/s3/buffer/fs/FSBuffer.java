package io.wrtn.engine.lucene.store.s3.buffer.fs;

import io.wrtn.engine.lucene.store.s3.storage.s3.S3Storage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

public class FSBuffer {

    private final FSDirectory dir;
    S3Storage storage;

    public FSBuffer(FSBufferConfig config, S3Storage storage) throws IOException {
        this.storage = storage;
        this.dir = FSDirectory.open(Paths.get(config.dir()));
    }

    public String[] listAll() throws IOException {
        return dir.listAll();
    }

    public Boolean exists(String name) {
        return Files.exists(dir.getDirectory().resolve(name));
    }

    public void deleteFile(String name) throws IOException {
        dir.deleteFile(name);
    }

    public void deleteFiles(List<String> names) throws IOException {
        for (String name : names) {
            dir.deleteFile(name);
        }
    }

    public long fileLength(String name) throws IOException {
        return dir.fileLength(name);
    }

    public IndexOutput createOutput(String name, IOContext context, boolean temp) throws IOException {
        return dir.createOutput(name, context);
    }

    public IndexInput openInput(String name, IOContext context) throws IOException {
        return dir.openInput(name, context);
    }

    public void sync(String name) throws IOException {
        storage.writeFromFile(name, dir.getDirectory().resolve(name));
    }

    public void batchSync(List<String> names) throws IOException {
        List<Path> filePaths = new ArrayList<>();
        for (String name : names) {
            filePaths.add(dir.getDirectory().resolve(name));
        }

        storage.writeFromFiles(names, filePaths);
    }

    public void rename(String from, String to) throws IOException {
        dir.rename(from, to);
    }

    public void close() throws IOException {
        dir.close();
    }
}
