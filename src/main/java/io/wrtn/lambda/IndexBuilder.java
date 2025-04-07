package io.wrtn.lambda;

import com.beust.jcommander.JCommander;
import io.wrtn.infra.aws.DynamoDB;
import io.wrtn.infra.aws.EFS;

import io.wrtn.infra.aws.S3;
import io.wrtn.model.wal.PendingDeletes;
import java.util.*;

import io.wrtn.engine.lucene.IndexBuildHelper;
import io.wrtn.model.wal.WalRecord;
import io.wrtn.model.event.IndexBuildEvent;
import io.wrtn.model.index.Index;
import sun.misc.Signal;
import io.wrtn.util.*;

import static io.wrtn.util.Constants.Config.EFS_MOUNT_PATH;
import static io.wrtn.util.Constants.Config.INDEX_BUILDER_TIMEOUT;
import static io.wrtn.util.Constants.IndexStatus.INDEX_STATUS_ACTIVE;
import static io.wrtn.util.Constants.IndexStatus.INDEX_STATUS_DELETING;
import static io.wrtn.util.TimeUtil.getCurrentUnixEpoch;
import static io.wrtn.util.TimeUtil.getIndexBuilderFailureTimeout;
import static io.wrtn.util.Constants.Config.INDEX_BUILD_INTERVAL;

public class IndexBuilder {

    public static void main(String[] args) {

        GlobalLogger.initialize(true);

        final DynamoDB ddbClient = new DynamoDB();
        final S3 s3Client = new S3();
        final EFS efsClient = new EFS(s3Client);

        IndexBuilderArgParser params = new IndexBuilderArgParser();
        JCommander cmd = new JCommander(params);
        cmd.parse(args);
        IndexBuildEvent event = params.getEvent();

        Signal.handle(new Signal("TERM"), sig -> {
            GlobalLogger.info("Got SIGTERM, try to unlock");
            Index index = new Index();
            index.setProjectId(event.getProjectId());
            index.setIndexName(event.getIndexName());
            index.setWriteLocked(false);
            index.setWriteLockedAt(0L);
            try {
                ddbClient.updateIndexIfWriteLocked(index);
            } catch (GlobalExceptionHandler e) {
                GlobalLogger.error(
                    "DynamoDB update failed during signal handling: " + e.getMessage());
            }

            System.exit(143);
        });

        long startTimeSeconds = System.currentTimeMillis() / 1000;
        IndexBuildHelper indexBuildHelper = null;
        try {
            validateEvent(event);

            // Update index metadata to acquire write lock
            Index index = new Index();
            index.setProjectId(event.getProjectId());
            index.setIndexName(event.getIndexName());
            index.setWriteLocked(true);
            index.setWriteLockedAt(getCurrentUnixEpoch());
            index.setUpdatedAt(index.getWriteLockedAt());

            GlobalLogger.info("Update index metadata to start building");
            Index updatedIndex = ddbClient.updateIndexIfWritable(index);
            if (updatedIndex == null) {
                // This index has been deleted, exit without further processing
                return;
            }

            if (updatedIndex.isConditionCheckFailed()) {
                // Index is not writable: !ACTIVE and/or write locked or not expired
                String status = updatedIndex.getIndexStatus();
                if (status.equals(INDEX_STATUS_ACTIVE)) {
                    if (updatedIndex.getWriteLocked()) {
                        long timeDiff = getCurrentUnixEpoch() - updatedIndex.getWriteLockedAt();
                        if (timeDiff > getIndexBuilderFailureTimeout()) {
                            GlobalLogger.info("Force update index metadata to start building");
                            Index indexMeta = new Index();
                            indexMeta.setProjectId(updatedIndex.getProjectId());
                            indexMeta.setIndexName(updatedIndex.getIndexName());
                            indexMeta.setWriteLocked(true);
                            indexMeta.setWriteLockedAt(getCurrentUnixEpoch());
                            indexMeta.setUpdatedAt(updatedIndex.getWriteLockedAt());
                            ddbClient.updateIndex(indexMeta);
                        } else {
                            GlobalLogger.info("Index is write-locked, retry later");
                            // Retry later
                            return;
                        }
                    } else {
                        // !INDEX_STATUS_ACTIVE && writeLocked false
                        throw new GlobalExceptionHandler("Invalid status: " + status,
                            StatusCode.SERVER_ERROR);
                    }
                } else if (status.equals(INDEX_STATUS_DELETING)) {
                    if (updatedIndex.getWriteLocked()) {
                        // Index building was started before the index deletion triggered
                        long timeDiff = getCurrentUnixEpoch() - updatedIndex.getWriteLockedAt();
                        if (timeDiff > getIndexBuilderFailureTimeout()) {
                            GlobalLogger.info(
                                "Index is being deleted, force update write-lock and exit");
                            // The IndexBuilder holding the lock failed for some reason
                            // Force updating index metadata so that index deletion handler can make progress
                            Index indexMeta = new Index();
                            indexMeta.setProjectId(updatedIndex.getProjectId());
                            indexMeta.setIndexName(updatedIndex.getIndexName());
                            indexMeta.setWriteLocked(false);
                            indexMeta.setWriteLockedAt(0L);
                            indexMeta.setUpdatedAt(getCurrentUnixEpoch());
                            ddbClient.updateIndex(indexMeta);
                        }
                    }

                    GlobalLogger.info("Index is being deleted, exit");
                    // This index is being deleted, exit without resending
                    return;
                } else {
                    // !INDEX_STATUS_ACTIVE && !INDEX_STATUS_DELETING
                    throw new GlobalExceptionHandler("Invalid status: " + status,
                        StatusCode.SERVER_ERROR);
                }
            }

            GlobalLogger.info(
                "Start creating IndexBuilderHelper currentSize: " + updatedIndex.getStorageInfo()
                    .getSize());
            // Create index building helper
            indexBuildHelper = new IndexBuildHelper(updatedIndex, event.getShardId());

            String walPath = PathBuilder.buildFsWalPath(EFS_MOUNT_PATH, index,
                event.getShardId());
            efsClient.deleteTempPendingDeletes(walPath);
            PendingDeletes pendingDeletes = efsClient.getPendingDeletes(walPath);

            GlobalLogger.info(
                "Start writing messages to the index " + index.getProjectId() + "."
                    + index.getIndexName());
            boolean unprocessedWalLeft = false;
            List<WalRecord> walRecords = new ArrayList<>();
            List<WalRecord> processedWalRecords;
            while (!isTimeoutClose(startTimeSeconds)) {
                long intervalStart = System.currentTimeMillis();

                efsClient.deleteStaleWalRecords(walPath, pendingDeletes.getWalIdMap());
                if (!unprocessedWalLeft) {
                    // Fetch messages from the corresponding index data shard queue
                    GlobalLogger.info("Start reading WAL records");
                    walRecords = efsClient.readWalRecords(walPath, pendingDeletes.getWalIdMap());
                }

                // Process the messages
                if (walRecords.isEmpty()) {
                    GlobalLogger.info("Exit due to no more WAL records to read");
                    break;
                }

                GlobalLogger.info("Start processing " + walRecords.size() + " WAL records");
                processedWalRecords = indexBuildHelper.processWalRecords(walRecords);

                GlobalLogger.info(
                    "Start committing to storage: " + processedWalRecords.size()
                        + " messages, open files " + getOpenFileDescriptorCount());
                // Commit the processed documents
                updatedIndex.setDataUpdatedAt(getCurrentUnixEpoch());
                indexBuildHelper.commit(updatedIndex);

                // Get index storage stats
                GlobalLogger.info("Getting index stats");
                int numDocs = indexBuildHelper.getNumDocs();
                long indexSize = indexBuildHelper.getStorageSize();

                GlobalLogger.info("Start updating the index metadata after size: " + indexSize);
                // Set index to update
                Index indexMeta = new Index();
                indexMeta.setProjectId(updatedIndex.getProjectId());
                indexMeta.setIndexName(updatedIndex.getIndexName());
                indexMeta.setNumDocs(numDocs);
                updatedIndex.getStorageInfo().setSize(indexSize);
                indexMeta.setStorageInfo(updatedIndex.getStorageInfo());
                indexMeta.setUpdatedAt(getCurrentUnixEpoch());
                indexMeta.setDataUpdatedAt(updatedIndex.getDataUpdatedAt());
                updatedIndex = ddbClient.updateIndexIfWriteLocked(indexMeta);
                if (updatedIndex.isConditionCheckFailed()) {
                    throw new GlobalExceptionHandler("Index is not locked",
                        StatusCode.SERVER_ERROR);
                }

                GlobalLogger.info("Start deleting WAL records: " + processedWalRecords.size());
                // Soft-delete the processed WAL files in EFS
                long currentTimeMillis = System.currentTimeMillis();
                for (WalRecord walRecord : processedWalRecords) {
                    pendingDeletes.getWalIdMap().put(walRecord.getId(), currentTimeMillis);
                }
                efsClient.putPendingDeletes(walPath, pendingDeletes);

                // We do not delete WAL payloads in S3 for concurrent WAL readers.
                // These objects will be deleted by the S3 lifecycle policy

                if (processedWalRecords.equals(walRecords) || walRecords.isEmpty()) {
                    unprocessedWalLeft = false;
                    Thread.sleep(getBuildIntervalMillis(intervalStart));
                } else {
                    unprocessedWalLeft = true;
                }
                processedWalRecords.clear();
            }

            // Final pending_deletes sync
            efsClient.putPendingDeletes(walPath, pendingDeletes);

            GlobalLogger.info("Start closing the index build helper");
            // Clear resources: wait for potential Lucene segments merge
            indexBuildHelper.close(updatedIndex);

            GlobalLogger.info("Start updating the index metadata before exiting");
            // Set index to update
            Index indexMeta = new Index();
            indexMeta.setProjectId(updatedIndex.getProjectId());
            indexMeta.setIndexName(updatedIndex.getIndexName());
            indexMeta.setWriteLocked(false);
            indexMeta.setWriteLockedAt(0L);
            updatedIndex.getStorageInfo().setSize(indexBuildHelper.getStorageSize());
            indexMeta.setStorageInfo(updatedIndex.getStorageInfo());
            indexMeta.setUpdatedAt(getCurrentUnixEpoch());
            if (ddbClient.updateIndexIfWriteLocked(indexMeta).isConditionCheckFailed()) {
                throw new GlobalExceptionHandler("Index is not locked",
                    StatusCode.SERVER_ERROR);
            }

        } catch (GlobalExceptionHandler e) {
            if (indexBuildHelper != null) {
                indexBuildHelper.closeExecutors();
            }
            // Runtime error throw GlobalException
            GlobalLogger.error("Global exception occurs");
            GlobalLogger.error(e.getErrorCode().getMessage() + " : " + e.getMessage());

            throw new RuntimeException("Global exception occurs: " + e.getMessage());
        } catch (Exception e) {
            if (indexBuildHelper != null) {
                indexBuildHelper.closeExecutors();
            }
            GlobalLogger.fatal("Unhandled exception occurs: " + event);
            GlobalLogger.fatal(Arrays.toString(e.getStackTrace()));
            GlobalLogger.fatal(e.getMessage());

            throw new RuntimeException("Unhandled exception occurs: " + e.getMessage());
        }
    }

    private static void validateEvent(IndexBuildEvent event) throws GlobalExceptionHandler {

        if (event.getProjectId() == null || event.getProjectId().isEmpty()) {
            throw new GlobalExceptionHandler("projectId cannot be null/empty value",
                StatusCode.INVALID_INPUT_VALUE);
        }

        if (event.getIndexName() == null || event.getIndexName().isEmpty()) {
            throw new GlobalExceptionHandler("indexName cannot be null/empty value",
                StatusCode.INVALID_INPUT_VALUE);
        }
    }

    private static boolean isTimeoutClose(long startTimeSeconds) {
        return ((System.currentTimeMillis() / 1000) - startTimeSeconds) >= (INDEX_BUILDER_TIMEOUT
            - 600);
    }

    private static long getBuildIntervalMillis(long intervalStart) {
        long elapsed = System.currentTimeMillis() - intervalStart;
        return Math.max(0, INDEX_BUILD_INTERVAL * 1000L - elapsed);
    }

    public static int getOpenFileDescriptorCount() {
        java.io.File fdDir = new java.io.File("/proc/self/fd");
        return Objects.requireNonNull(fdDir.listFiles()).length;
    }
}


