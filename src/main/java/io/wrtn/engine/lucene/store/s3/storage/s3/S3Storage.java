package io.wrtn.engine.lucene.store.s3.storage.s3;

import static io.wrtn.engine.lucene.Constants.MAX_STORAGE_REQUEST_AT_ONCE;
import static org.apache.lucene.index.IndexFileNames.parseSegmentName;

import io.wrtn.model.storage.StorageMetadata;
import io.wrtn.util.GlobalLogger;
import io.wrtn.util.PathBuilder;
import io.wrtn.util.Threads;
import java.io.IOException;
import java.nio.file.Files;
import io.wrtn.model.storage.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.Path;
import java.util.*;

/**
 * A Storage implementation for AWS S3.
 */
public class S3Storage {

    private final String bucket;

    private final S3AsyncClient s3Client;
    private final S3Client s3SyncClient;

    private StorageMetadata meta;

    /**
     * Creates and initializes a new S3 Storage object.
     *
     * @param config the parameters required to initialize S3 clients, not null
     */
    public S3Storage(final S3StorageConfig config, final StorageMetadata meta) {
        this.bucket = config.bucket();
        this.meta = meta;
        this.s3Client = config.s3().getClient();
        this.s3SyncClient = config.s3().getSyncClient();
    }

    /**
     * Lists all the object names excluding the prefix part in the configured S3 bucket prefix.
     *
     * @return the object names array
     */
    public String[] listAll() {
        return meta.getFileMap().keySet().toArray(new String[0]);
    }

    /**
     * Gets object length matched with the provided name.
     */
    public long fileLength(final String name) {
        return meta.getFileMap().get(name).getSize();
    }

    public boolean exists(final String name) {
        return meta.getFileMap().containsKey(name);
    }

    /**
     * Deletes object matched with the provided name.
     */
    public void deleteFile(final String name) {

        s3Client.deleteObject(b -> b.bucket(bucket).key(PathBuilder.buildObjectKeyForWrite(
            meta, buildPrefixedName(name)))).join();

        removeFile(name);
    }

    public ResponseInputStream<GetObjectResponse> getObjectStream(String name) {
        return s3SyncClient.getObject(GetObjectRequest.builder()
            .bucket(bucket)
            .key(PathBuilder.buildObjectKeyForRead(meta, name))
            .versionId(getVersionId(name))
            .build(), ResponseTransformer.toInputStream());
    }

    /**
     * Reads a whole object and writes to a file.
     *
     * @param name     the object name
     * @param filePath the file path to be written
     */
    public void readToFile(final String name, final Path filePath) {
        s3SyncClient.getObject(
            req -> req.bucket(bucket).key(PathBuilder.buildObjectKeyForRead(meta, name))
                .versionId(getVersionId(name)),
            ResponseTransformer.toFile(filePath));
    }

    public void writeFromFile(final String name, final Path filePath) {
        String prefixedName = buildPrefixedName(name);
        PutObjectResponse resp = s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket).key(PathBuilder.buildObjectKeyForWrite(meta, prefixedName)).build(),
            AsyncRequestBody.fromFile(filePath)).join();

        // Reflect the file metadata to the storage metadata
        addFile(name, prefixedName, filePath, resp.versionId());
    }

    public void writeFromFiles(final List<String> names, final List<Path> filePaths) {

        List<PutObjectResponse> resps = new ArrayList<>();
        List<String> prefixedNames = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Path filePath = filePaths.get(i);
            String prefixedName = buildPrefixedName(name);
            prefixedNames.add(prefixedName);

            resps.add(s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket).key(PathBuilder.buildObjectKeyForWrite(meta, prefixedName)).build(),
                AsyncRequestBody.fromFile(filePath)).join());
        }

        assert names.size() == resps.size();

        for (int i = 0; i < resps.size(); i++) {
            addFile(names.get(i), prefixedNames.get(i), filePaths.get(i), resps.get(i).versionId());
        }
    }

    /**
     * Releases the created S3 clients.
     */
    public void close() {
    }

    public long getStorageSize() {
        return meta.getTotalSize();
    }

    public StorageMetadata getMeta() {
        return meta;
    }

    public void setMeta(StorageMetadata newSnapshot) {
        meta = newSnapshot;
    }

    private void addFile(String name, String prefixedName, Path filePath, String versionId) {
        long fileLength = filePath.toFile().length();
        File file = new File(prefixedName, fileLength, versionId, meta.getCurrentPrefixId());
        meta.getFileMap().put(name, file);
        meta.setTotalSize(meta.getTotalSize() + fileLength);
    }

    private void removeFile(String name) {
        long fileLength = meta.getFileMap().get(name).getSize();
        meta.getFileMap().remove(name);
        meta.setTotalSize(meta.getTotalSize() - fileLength);
    }

    private String getVersionId(String name) {
        return meta.getFileMap().get(name).getVersionId();
    }

    private String buildPrefixedName(String name) {
        return name.startsWith("segments_") ? name : parseSegmentName(name) + "/" + name;
    }

    private class ReadToFileTask implements Callable<Void> {

        private final String name;
        private final Path filePath;

        public ReadToFileTask(String name, Path filePath) {
            this.name = name;
            this.filePath = filePath;
        }

        @Override
        public Void call() throws IOException {
            ResponseInputStream<GetObjectResponse> resp = getObjectStream(name);
            Files.copy(resp, filePath);
            resp.close();

            return null;
        }
    }
}