package io.wrtn.engine.lucene.store.s3;

import io.wrtn.engine.lucene.store.s3.buffer.fs.FSBuffer;
import io.wrtn.engine.lucene.store.s3.cache.fs.FSCache;
import io.wrtn.engine.lucene.store.s3.storage.s3.S3Storage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import io.wrtn.engine.lucene.store.s3.lock.NoopLock;
import org.apache.lucene.store.*;

import static org.apache.lucene.index.IndexFileNames.PENDING_SEGMENTS;

/**
 * A S3 based implementation of a Lucene <code>Directory</code> allowing the storage of a Lucene
 * index within S3. The directory works against a single object prefix, where the binary data is
 * stored in <code>objects</code>. Each "object" has an entry in the S3.
 */
public class S3Directory extends Directory {

    private volatile boolean isOpen = true;

    private final S3Storage storage;
    private final FSCache cache;
    private final FSBuffer buffer;
    private final Set<String> bufferedFileMap;

    /**
     * Used to generate temp file names in {@link #createTempOutput}.
     */
    private final AtomicLong nextTempFileCounter = new AtomicLong();

    /**
     * Creates a new S3 directory with the provided block size.
     *
     * @param storage the storage instance
     * @param cache   the cache instance
     * @param buffer  the buffer instance
     */
    public S3Directory(final S3Storage storage, final FSCache cache, final FSBuffer buffer) {
        super();
        this.storage = storage;
        this.cache = cache;
        this.buffer = buffer;
        this.bufferedFileMap = ConcurrentHashMap.newKeySet();
    }

    @Override
    public String[] listAll() throws IOException {
        ensureOpen();

        // Get file list in both buffer and storage
        String[] storageFiles = storage.listAll();
        String[] bufferFiles = buffer.listAll();

        // Assumes files are not duplicated between buffer and storage
        String[] files = Stream.concat(
            Arrays.stream(storageFiles),
            Arrays.stream(bufferFiles)
        ).toArray(String[]::new);

        Arrays.sort(files);

        return files;
    }

    @Override
    public void deleteFile(final String name) throws IOException {
        if (buffer.exists(name)) {
            buffer.deleteFile(name);
        }

        if (cache.exists(name)) {
            cache.deleteFile(name);
        }

        if (storage.exists(name)) {
            storage.deleteFile(name);
        }
    }

    @Override
    public long fileLength(final String name) throws IOException {
        ensureOpen();

        if (buffer.exists(name)) {
            return buffer.fileLength(name);
        } else {
            return storage.fileLength(name);
        }
    }

    @Override
    public IndexOutput createOutput(final String name, final IOContext context) throws IOException {
        ensureOpen();
        bufferedFileMap.add(name);
        return buffer.createOutput(name, context, false);
    }

    @Override
    public IndexOutput createTempOutput(String prefix, String suffix, IOContext context)
        throws IOException {
        ensureOpen();
        while (true) {
            try {
                String name = getTempFileName(prefix, suffix,
                    nextTempFileCounter.getAndIncrement());
                return buffer.createOutput(name, context, true);
            } catch (
                @SuppressWarnings("unused")
                FileAlreadyExistsException faee) {
                // Retry with next incremented name
            }
        }
    }

    @Override
    public void sync(final Collection<String> names) throws IOException {
        ensureOpen();

        List<String> namesToSync = new ArrayList<>();
        // Sync all the requested buffered files that have not been written to storage yet
        for (String name : names) {
            // Do not sync temporary files
            if (bufferedFileMap.contains(name)) {
                bufferedFileMap.remove(name);
                if (!name.startsWith(PENDING_SEGMENTS)) {
                    if (buffer.exists(name)) {
                        // Check if the file exists in buffer.
                        // Files generated during merge already synced to storage.
                        namesToSync.add(name);
                    }
                }
            }
        }

        if (!namesToSync.isEmpty()) {
            buffer.batchSync(namesToSync);
            buffer.deleteFiles(namesToSync);
        }
    }

    @Override
    public void syncMetaData() {
        ensureOpen();

        // All files are already synced to storage, so nothing to do here
    }

    @Override
    public void rename(final String from, final String to) throws IOException {
        ensureOpen();

        // A file can be located either buffer or storage
        if (buffer.exists(from)) {
            buffer.rename(from, to);
            // Assume rename is requested only for pending segments file
            buffer.sync(to);
            buffer.deleteFile(to);
        } else {
            throw new UnsupportedOperationException("Rename a file not in buffer is not supported");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        isOpen = false;
    }

    @Override
    public IndexInput openInput(final String name, final IOContext context) throws IOException {
        ensureOpen();
        if (buffer.exists(name)) {
            return buffer.openInput(name, context);
        } else {
            return cache.openInput(name, context);
        }
    }

    @Override
    public Set<String> getPendingDeletions() {
        return Collections.emptySet();
    }

    @Override
    public final Lock obtainLock(String name) {
        return new NoopLock();
    }

    @Override
    protected final void ensureOpen() throws AlreadyClosedException {
        if (!isOpen) {
            throw new AlreadyClosedException("this Directory is closed");
        }
    }
}
