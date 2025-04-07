package io.wrtn.infra.aws;

import static io.wrtn.infra.aws.Constants.S3.TEMP_BUCKET;
import static io.wrtn.util.Constants.CommandType.DOCUMENT_UPSERT;
import static io.wrtn.util.Constants.Config.DEFAULT_LAMBDA_TIMEOUT;
import static io.wrtn.util.JsonParser.gson;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

import io.wrtn.dto.UpsertRequest;
import io.wrtn.util.GlobalLogger;
import io.wrtn.util.PathBuilder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import io.wrtn.model.wal.PendingDeletes;
import io.wrtn.model.wal.WalRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import io.wrtn.util.Threads;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public final class EFS {

    private static final String TMP_FILE_SUFFIX = ".tmp";
    private static final String PENDING_DELETE_FILE_NAME = "pending_deletes";
    private final S3 s3Client;

    public EFS(final S3 s3Client) {
        this.s3Client = s3Client;
    }

    public boolean createDirectoryIfNotExists(String path) throws IOException {
        File dir = Paths.get(path).toFile();
        Collection<File> files = FileUtils.listFiles(dir, TrueFileFilter.INSTANCE,
            TrueFileFilter.INSTANCE);
        for (File file : files) {
            if (!file.getName().startsWith(".nfs")) {
                return false;
            }
        }
        Files.createDirectories(dir.toPath());

        return true;
    }

    public void deleteDirectory(String path) throws IOException {
        File dir = Paths.get(path).toFile();
        try {
            FileUtils.deleteDirectory(dir);
        } catch (IOException e) {
            Collection<File> files = FileUtils.listFiles(dir, TrueFileFilter.INSTANCE,
                TrueFileFilter.INSTANCE);
            for (File file : files) {
                if (!file.getName().startsWith(".nfs")) {
                    FileUtils.delete(file);
                }
            }
        }
    }

    public long sizeOfDirectory(String path) {
        return FileUtils.sizeOfDirectory(Paths.get(path).toFile());
    }

    public void syncWalRecord(String path, WalRecord walRecord) throws IOException {
        Path tmpPath = Paths.get(path + TMP_FILE_SUFFIX);
        try (FileChannel fileChannel = FileChannel.open(tmpPath, StandardOpenOption.WRITE,
            StandardOpenOption.CREATE_NEW)) {
            fileChannel.write(ByteBuffer.wrap(gson.toJson(walRecord).getBytes()));
            fileChannel.force(true); // Force the sync to disk
        }

        // Atomically move the written temp file to the desired name
        Files.move(tmpPath, Paths.get(path), ATOMIC_MOVE);
    }

    public void syncDirectory(String path) throws IOException {
        try (FileChannel fileChannel = FileChannel.open(Paths.get(path), StandardOpenOption.READ)) {
            fileChannel.force(true); // Force the sync to disk
        }
    }

    public List<Path> listValidWalPaths(String dir, Map<String, Long> deletedWalIdMap)
        throws IOException {

        Path p = Paths.get(dir);
        assert Files.isDirectory(p);

        List<Path> walRecordPaths = new ArrayList<>();
        try (Stream<Path> entries = Files.list(p)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                if (entry.getFileName().toString().endsWith(TMP_FILE_SUFFIX) ||
                    entry.getFileName().toString().equals(PENDING_DELETE_FILE_NAME)) {
                    continue;
                }

                String walId = entry.getFileName().toString();
                if (!deletedWalIdMap.containsKey(walId)) {
                    walRecordPaths.add(entry);
                }
            }
        }

        return walRecordPaths;
    }

    public List<WalRecord> readWalRecords(List<Path> walPaths)
        throws InterruptedException, ExecutionException {

        List<ReadWalRecordTask> tasks = new ArrayList<>();
        for (Path walPath : walPaths) {
            tasks.add(new ReadWalRecordTask(walPath));
        }

        List<WalRecord> walRecords = new ArrayList<>();
        for (Future<WalRecord> future : Threads.getIOExecutor().invokeAll(tasks)) {
            walRecords.add(future.get());
        }

        return walRecords;
    }

    public List<WalRecord> readWalRecords(String dir, Map<String, Long> deletedWalIdMap)
        throws IOException, InterruptedException, ExecutionException {

        // Called by IndexBuilder
        Path p = Paths.get(dir);
        assert Files.isDirectory(p);

        List<ReadWalRecordTask> tasks = new ArrayList<>();
        try (Stream<Path> entries = Files.list(p)) {
            for (Path entry : (Iterable<Path>) entries::iterator) {
                if (entry.getFileName().toString().endsWith(TMP_FILE_SUFFIX) ||
                    entry.getFileName().toString().equals(PENDING_DELETE_FILE_NAME)) {
                    continue;
                }

                String walId = entry.getFileName().toString();
                if (!deletedWalIdMap.containsKey(walId)) {
                    tasks.add(new ReadWalRecordTask(entry));
                }
            }
        }

        List<WalRecord> walRecords = new ArrayList<>();
        for (Future<WalRecord> future : Threads.getIOExecutor().invokeAll(tasks)) {
            walRecords.add(future.get());
        }

        return walRecords;
    }

    public void deleteStaleWalRecords(String dir, Map<String, Long> deletedWalIdMap)
        throws IOException {

        long currentTimeMillis = System.currentTimeMillis();
        long lambdaTimeoutMillis = DEFAULT_LAMBDA_TIMEOUT * 1000L;
        List<String> deletedWalIds = new ArrayList<>();
        boolean hardDeleted = false;
        for (Map.Entry<String, Long> entry : deletedWalIdMap.entrySet()) {
            String walId = entry.getKey();
            String path = dir + "/" + walId;
            long deletedTimeMillis = entry.getValue();
            if ((currentTimeMillis - deletedTimeMillis) > lambdaTimeoutMillis) {
                if (exists(path)) {
                    Files.delete(Paths.get(path));
                    hardDeleted = true;
                }
                deletedWalIds.add(walId);
            }
        }

        for (String walId : deletedWalIds) {
            deletedWalIdMap.remove(walId);
        }

        if (hardDeleted) {
            syncDirectory(dir);
        }
    }

    public void readWalPayloads(List<WalRecord> walRecords) {

        List<CompletableFuture<ResponseBytes<GetObjectResponse>>> futures = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        for (WalRecord walRecord : walRecords) {
            if (walRecord.getType().equals(DOCUMENT_UPSERT) && walRecord.getS3Payload()) {
                if (walRecord.getDocuments() == null) {
                    // Fetch the payload from S3
                    futures.add(s3Client.getObjectAsync(TEMP_BUCKET,
                        PathBuilder.buildStorageKeyPath(walRecord.getProjectId(),
                            walRecord.getIndexName(),
                            walRecord.getShardId(), walRecord.getId())));
                    indexes.add(walRecords.indexOf(walRecord));
                }
            }
        }

        for (Integer index : indexes) {
            WalRecord walRecord = walRecords.get(index);
            UpsertRequest payload = gson.fromJson(
                futures.get(indexes.indexOf(index)).join().asUtf8String(),
                UpsertRequest.class);
            walRecord.setDocuments(payload.getDocuments());
        }

    }

    public void putPendingDeletes(String dir, PendingDeletes pendingDeletes)
        throws IOException {

        // Create pending_deletes file
        String path = dir + "/" + PENDING_DELETE_FILE_NAME;
        writeFileAtomic(path, gson.toJson(pendingDeletes));
        syncDirectory(dir);
    }

    public void deleteTempPendingDeletes(String dir) throws IOException {
        String pendingDeletesTmpPath = dir + PENDING_DELETE_FILE_NAME + TMP_FILE_SUFFIX;
        if (exists(pendingDeletesTmpPath)) {
            Files.delete(Paths.get(pendingDeletesTmpPath));
            syncDirectory(dir);
        }
    }

    public PendingDeletes getPendingDeletes(String dir) throws IOException {

        String pendingDeletesPath = dir + PENDING_DELETE_FILE_NAME;
        PendingDeletes pendingDeletes;
        if (exists(pendingDeletesPath)) {
            Path path = Paths.get(pendingDeletesPath);
            pendingDeletes = gson.fromJson(Files.readString(path),
                PendingDeletes.class);
        } else {
            pendingDeletes = new PendingDeletes(new HashMap<>());
        }

        return pendingDeletes;
    }

    public boolean isEmpty(String dir) throws IOException {
        Path path = Paths.get(dir);
        assert Files.isDirectory(path);

        try (Stream<Path> entries = Files.list(path)) {
            return entries.filter(p -> !p.getFileName().toString().equals(PENDING_DELETE_FILE_NAME))
                .findFirst().isEmpty();
        }
    }

    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    private class ReadWalRecordTask implements Callable<WalRecord> {

        private final Path path;

        public ReadWalRecordTask(Path path) {
            this.path = path;
        }

        @Override
        public WalRecord call() throws IOException {
            try {
                WalRecord walRecord = gson.fromJson(Files.readString(path), WalRecord.class);
                if (walRecord.getSeqNo() == null) {
                    GlobalLogger.fatal("Invalid WAL record: " + path.getFileName());
                }
                return walRecord;
            } catch (Exception e) {
                GlobalLogger.fatal("Error reading WAL record: " + path.getFileName());
                throw e;
            }
        }
    }

    private void writeFileAtomic(String path, String payload) throws IOException {

        // Write the payload to a temp file
        Path tmpPath = Paths.get(path + TMP_FILE_SUFFIX);
        try (FileChannel fileChannel = FileChannel.open(tmpPath, StandardOpenOption.WRITE,
            StandardOpenOption.CREATE_NEW)) {
            fileChannel.write(ByteBuffer.wrap(payload.getBytes()));
            fileChannel.force(true); // Force the sync to disk
        }

        // Atomically move the written temp file to the desired path
        Files.move(tmpPath, Paths.get(path), ATOMIC_MOVE);
    }
}
