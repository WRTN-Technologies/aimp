package io.wrtn.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import io.wrtn.infra.aws.*;
import io.wrtn.model.index.CloneSourceInfo;
import io.wrtn.model.index.DeletedIndex;
import io.wrtn.model.project.Project;
import io.wrtn.model.storage.StorageMetadata;
import io.wrtn.util.GlobalLogger;
import io.wrtn.util.PathBuilder;
import java.io.IOException;
import io.wrtn.model.command.ControlCommand;
import io.wrtn.model.event.IndexBuildEvent;
import io.wrtn.model.index.Index;
import io.wrtn.model.index.StorageInfo;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;
import io.wrtn.util.UUID;

import java.util.*;

import static io.wrtn.infra.aws.Constants.Batch.*;
import static io.wrtn.infra.aws.Constants.S3.INDEX_BUCKET;
import static io.wrtn.infra.aws.Constants.SQS.MAX_DELAY_SECONDS;
import static io.wrtn.util.Constants.CommandType.*;
import static io.wrtn.util.Constants.Config.*;
import static io.wrtn.util.Constants.IndexClass.INDEX_CLASS_IA;
import static io.wrtn.util.Constants.IndexClass.INDEX_CLASS_STD;
import static io.wrtn.util.Constants.IndexStatus.INDEX_STATUS_ACTIVE;
import static io.wrtn.util.Constants.IndexStatus.INDEX_STATUS_DELETING;
import static io.wrtn.util.Constants.ProjectStatus.PROJECT_STATUS_ACTIVE;
import static io.wrtn.util.Constants.ProjectStatus.PROJECT_STATUS_DELETING;
import static io.wrtn.util.PathBuilder.buildStorageIndexPath;
import static io.wrtn.util.PathBuilder.buildStorageProjectPath;
import static io.wrtn.util.JsonParser.gson;
import static io.wrtn.util.PathBuilder.buildStorageShardPath;
import static io.wrtn.util.SizeConverter.mbToB;
import static io.wrtn.util.TimeUtil.getCurrentUnixEpoch;
import static io.wrtn.util.TimeUtil.getIndexBuilderFailureTimeout;

public class CommandController implements RequestHandler<SQSEvent, Void> {

    final DynamoDB ddbClient;
    final SQS sqsClient;
    final S3 s3Client;
    final Batch batchClient;
    final EFS efsClient;
    final Lambda lambdaClient;
    final ApiGateway apiGateway;

    public CommandController() {
        this.ddbClient = new DynamoDB();
        this.sqsClient = new SQS();
        this.s3Client = new S3();
        this.batchClient = new Batch();
        this.efsClient = new EFS(s3Client);
        this.lambdaClient = new Lambda();
        this.apiGateway = new ApiGateway();
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {

        try {
            GlobalLogger.initialize(false);

            List<ControlCommand> controlCommands = new ArrayList<>();
            // Get control commands from SQS messages
            for (SQSEvent.SQSMessage msg : event.getRecords()) {
                controlCommands.add(gson.fromJson(msg.getBody(), ControlCommand.class));
            }

            for (ControlCommand controlCommand : controlCommands) {
                switch (controlCommand.getType()) {
                    case PROJECT_CREATE -> projectCreateCommandHandler(controlCommand);
                    case PROJECT_DELETE -> projectDeleteCommandHandler(controlCommand);
                    case INDEX_CREATE -> indexCreateCommandHandler(controlCommand);
                    case INDEX_DELETE -> indexDeleteCommandHandler(controlCommand);
                    case INDEX_BUILD -> indexBuildCommandHandler();
                    case INDEX_RESCHED -> indexReschedCommandHandler();
                    default -> throw new GlobalExceptionHandler(
                        "Invalid command : " + controlCommand.getType(),
                        StatusCode.BAD_REQUEST);
                }
            }

            return null;
        } catch (GlobalExceptionHandler e) {
            // Runtime error throw GlobalException
            GlobalLogger.error(e.getErrorCode().getMessage() + " : " + e.getMessage());
            GlobalLogger.error(Arrays.toString(e.getStackTrace()));
            throw new RuntimeException("Unhandled exception occurs: " + e);
        } catch (Exception e) {
            // Internal Error
            GlobalLogger.fatal(e.getMessage());
            GlobalLogger.fatal(Arrays.toString(e.getStackTrace()));
            throw new RuntimeException("Unhandled exception occurs: " + e);
        }
    }

    private void projectCreateCommandHandler(ControlCommand controlCommand)
        throws GlobalExceptionHandler {
        Objects.requireNonNull(controlCommand.getProjectId());
        Objects.requireNonNull(controlCommand.getRateLimit());

        String projectId = controlCommand.getProjectId();
        Double rateLimit = controlCommand.getRateLimit();

        // Create API key with rate limit for this project
        String apiKey = apiGateway.createProjectApiKeyWithLimit(projectId, rateLimit);

        // Update project metadata in DynamoDB
        Project project = new Project();
        project.setId(projectId);
        project.setProjectApiKey(apiKey);
        project.setProjectStatus(PROJECT_STATUS_ACTIVE);
        project.setUpdatedAt(getCurrentUnixEpoch());

        Project updatedProject = ddbClient.updateProjectIfCreating(project);
        if (updatedProject == null) {
            // This index has been unexpectedly deleted by another context
            throw new GlobalExceptionHandler("Project has been deleted while creating: " + project,
                StatusCode.SERVER_ERROR);
        } else if (updatedProject.isConditionCheckFailed()) {
            String status = updatedProject.getProjectStatus();
            if (!status.equals(PROJECT_STATUS_ACTIVE) && !status.equals(PROJECT_STATUS_DELETING)) {
                throw new GlobalExceptionHandler("Invalid project status: " + status,
                    StatusCode.SERVER_ERROR);
            }
        }
    }

    private void projectDeleteCommandHandler(ControlCommand controlCommand)
        throws GlobalExceptionHandler, IOException {
        Objects.requireNonNull(controlCommand.getProjectId());

        String projectId = controlCommand.getProjectId();

        // Delete active indexes belong to this project
        List<Index> indexList = ddbClient.listIndexesByProjectIdConsistent(projectId);
        boolean needRetry = false;
        for (Index index : indexList) {
            // Try to delete the index
            if (!deleteIndex(index.getProjectId(), index.getIndexName())) {
                needRetry = true;
            }
        }

        if (indexList.isEmpty() || !needRetry) {
            // Safe to delete project
            s3Client.deleteObjectsWithPrefix(INDEX_BUCKET, buildStorageProjectPath(projectId));
            apiGateway.deleteProjectApiKey(projectId);
            ddbClient.deleteProject(projectId);
        } else {
            // Resend this command to check this project is deletable again
            GlobalLogger.info("Retry later to delete project " + projectId);
            sqsClient.sendControlCommandWithDelay(controlCommand,
                Math.min(getIndexBuilderFailureTimeout(), MAX_DELAY_SECONDS));
        }

        // Delete efs directory for index mutation log
        efsClient.deleteDirectory(PathBuilder.buildFsProjectPath(EFS_MOUNT_PATH, projectId));
    }

    private void indexCreateCommandHandler(ControlCommand command)
        throws GlobalExceptionHandler, IOException {
        Objects.requireNonNull(command);

        String projectId = command.getProjectId();
        String indexName = command.getIndexName();
        CloneSourceInfo cloneSourceInfo = command.getCloneSourceInfo();

        // Create directory for index data
        if (!s3Client.createIndexPrefixIfNotExists(INDEX_BUCKET, projectId, indexName)) {
            GlobalLogger.error(
                "Index prefix is not empty: " + buildStorageIndexPath(projectId, indexName));
        }

        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setPathPrefix(buildStorageIndexPath(projectId, indexName));
        if (cloneSourceInfo == null) {
            storageInfo.setSize(0L);
        } else {
            StorageMetadata sourceMeta = gson.fromJson(
                s3Client.getVersionedObject(INDEX_BUCKET, PathBuilder.buildMetaKey(
                        cloneSourceInfo.getSourceProjectId(),
                        cloneSourceInfo.getSourceIndexName(), TEMPORARY_SHARD_ID),
                    cloneSourceInfo.getSourceIndexVersionId()).asUtf8String(),
                StorageMetadata.class);

            sourceMeta.addPrefix(buildStorageShardPath(projectId, indexName, TEMPORARY_SHARD_ID));
            sourceMeta.setComputeNodeIds(List.of(0));
            s3Client.putObject(INDEX_BUCKET,
                PathBuilder.buildMetaKey(projectId, indexName, TEMPORARY_SHARD_ID),
                sourceMeta);

            storageInfo.setSize(sourceMeta.getTotalSize());
        }

        // Create efs directory for index mutation log
        if (!efsClient.createDirectoryIfNotExists(
            PathBuilder.buildFsWalPath(EFS_MOUNT_PATH, projectId, indexName, TEMPORARY_SHARD_ID))) {
            GlobalLogger.warn(
                "Index mutation log directory is not empty: " + PathBuilder.buildFsWalPath(
                    EFS_MOUNT_PATH, projectId, indexName, TEMPORARY_SHARD_ID));
        }

        // Update index metadata in DynamoDB
        Index index = new Index();
        index.setProjectId(projectId);
        index.setIndexName(indexName);
        index.setStorageInfo(storageInfo);
        index.setIndexStatus(INDEX_STATUS_ACTIVE);
        index.setUpdatedAt(getCurrentUnixEpoch());

        Index updatedIndex = ddbClient.updateIndexIfCreating(index);
        if (updatedIndex == null) {
            // This index has been unexpectedly deleted by another context
            throw new GlobalExceptionHandler("Index has been deleted while creating: " + index,
                StatusCode.SERVER_ERROR);
        } else if (updatedIndex.isConditionCheckFailed()) {
            String status = updatedIndex.getIndexStatus();
            if (!status.equals(INDEX_STATUS_ACTIVE) && !status.equals(INDEX_STATUS_DELETING)) {
                throw new GlobalExceptionHandler("Invalid index status: " + status,
                    StatusCode.SERVER_ERROR);
            }
        }
    }

    private void indexDeleteCommandHandler(ControlCommand command)
        throws GlobalExceptionHandler, IOException {
        Objects.requireNonNull(command);

        if (!deleteIndex(command.getProjectId(), command.getIndexName())) {
            // Resend this command to check this index is deletable again
            GlobalLogger.info("Retry later to delete index " + command.getProjectId() + "."
                + command.getIndexName());
            sqsClient.sendControlCommandWithDelay(command,
                Math.min(getIndexBuilderFailureTimeout(), MAX_DELAY_SECONDS));
        }
    }

    private void indexBuildCommandHandler() throws GlobalExceptionHandler, IOException {
        // Scan index metadata table
        List<Index> indexes = ddbClient.listIndexesConsistent();

        // Invoke index builder async for each index with outstanding data commands
        for (Index index : indexes) {
            if (index.getIndexStatus().equals(INDEX_STATUS_ACTIVE)) {
                String path = PathBuilder.buildFsWalPath(EFS_MOUNT_PATH, index,
                    TEMPORARY_SHARD_ID);
                if (efsClient.exists(path) && !efsClient.isEmpty(path)) {
                    long timeDiff = getCurrentUnixEpoch() - index.getWriteLockedAt();
                    if (!index.getWriteLocked() ||
                        timeDiff > getIndexBuilderFailureTimeout()) {
                        IndexBuildEvent indexBuildEvent = new IndexBuildEvent();
                        indexBuildEvent.setProjectId(index.getProjectId());
                        indexBuildEvent.setIndexName(index.getIndexName());
                        indexBuildEvent.setShardId(TEMPORARY_SHARD_ID);
                        batchClient.submitIndexBuilderJob(indexBuildEvent,
                            index.getIndexClass(),
                            INDEX_BUILDER_JOB_NAME_PREFIX, INDEX_BUILDER_JOB_QUEUE_NAME
                        );
                    }
                }
            } else if (index.getIndexStatus().equals(INDEX_STATUS_DELETING)) {
                if (index.getWriteLocked()) {
                    // Check whether the write-lock need to be released forcefully
                    long timeDiff = getCurrentUnixEpoch() - index.getWriteLockedAt();
                    if (timeDiff > getIndexBuilderFailureTimeout()) {
                        GlobalLogger.info("Index is being deleted, force update write-lock");
                        // The IndexBuilder holding the lock failed for some reason
                        // Force updating index metadata so that index deletion handler can make progress
                        index.setWriteLocked(false);
                        index.setWriteLockedAt(0L);
                        index.setUpdatedAt(getCurrentUnixEpoch());
                        ddbClient.updateIndex(index);
                    }
                }
            }
        }
    }

    private void indexReschedCommandHandler()
        throws GlobalExceptionHandler {
        // Scan index metadata table
        List<Index> indexes = ddbClient.listActiveIndexes();
        if (indexes.isEmpty()) {
            return;
        }
        List<Index> indexesStd = new ArrayList<>();
        List<Index> indexesIa = new ArrayList<>();
        for (Index index : indexes) {
            if (index.getIndexClass().equals(INDEX_CLASS_STD)) {
                indexesStd.add(index);
            } else if (index.getIndexClass().equals(INDEX_CLASS_IA)) {
                indexesIa.add(index);
            }
        }

        // Reschedule indexes belong to standard class
        schedIndexes(indexesStd, INDEX_CLASS_STD);

        // Reschedule indexes belong to infrequent_access class
        schedIndexes(indexesIa, INDEX_CLASS_IA);
    }

    private void schedIndexes(List<Index> indexes, String indexClass) throws GlobalExceptionHandler {

        int nodeMemSize;
        float memToStorageRatio;
        int maxComputeNodes;
        if (indexClass.equals(INDEX_CLASS_STD)) {
            nodeMemSize = MEMORY_SIZE_PER_COMPUTE_NODE_STD;
            memToStorageRatio = DESIRED_MEMORY_TO_STORAGE_RATIO_STD;
            maxComputeNodes = MAX_NUM_COMPUTE_NODES_STD;
        } else if (indexClass.equals(INDEX_CLASS_IA)) {
            nodeMemSize = MEMORY_SIZE_PER_COMPUTE_NODE_IA;
            memToStorageRatio = DESIRED_MEMORY_TO_STORAGE_RATIO_IA;
            maxComputeNodes = MAX_NUM_COMPUTE_NODES_IA;
        } else {
            throw new RuntimeException("Invalid index class: " + indexClass);
        }

        // Decide number of compute nodes based on total storage size
        long totalStorageSize = 0;
        long storageSizePerComputeNode = (long) (mbToB(nodeMemSize)
            / memToStorageRatio);

        // Map<Index, ShardIds>
        Map<Index, List<Integer>> indexComputeNodeIdsMap = new HashMap<>();
        // All the compute node IDs deduplicated
        Set<Integer> computeNodeIds = new HashSet<>();
        // All the index shards
        List<IndexShard> indexShards = new ArrayList<>();
        for (Index index : indexes) {
            List<Integer> indexComputeNodeIds = index.getComputeNodeIds();

            // Remove compute node IDs that are greater than maxComputeNodes
            indexComputeNodeIds.removeIf(nodeId -> nodeId >= maxComputeNodes);

            computeNodeIds.addAll(indexComputeNodeIds);
            indexComputeNodeIdsMap.put(index, indexComputeNodeIds);

            long indexSize = index.getStorageInfo().getSize();
            totalStorageSize += indexSize;
            if (indexSize > storageSizePerComputeNode) {
                long numIndexShards = Math.ceilDiv(indexSize, storageSizePerComputeNode);
                long sizePerIndexShard = Math.ceilDiv(indexSize, numIndexShards);
                while (numIndexShards-- > 0) {
                    indexShards.add(new IndexShard(index, sizePerIndexShard));
                }
            } else {
                indexShards.add(new IndexShard(index, indexSize));
            }
        }

        // Sort index shards by storage size in descending order
        indexShards.sort(Comparator.comparingLong(IndexShard::getShardSize).reversed());

        int numComputeNodes = (int) Math.ceilDiv(totalStorageSize,
            storageSizePerComputeNode);
        if (numComputeNodes > maxComputeNodes) {
            GlobalLogger.warn("Not enough compute nodes for " + indexClass
                + " " + numComputeNodes + " > " + maxComputeNodes);
            numComputeNodes = maxComputeNodes;
        }

        GlobalLogger.info("Start rescheduling " + indexShards.size() + " index shards to "
            + numComputeNodes + " compute nodes");

        // Initialize compute nodes
        List<ComputeNode> computeNodes = new ArrayList<>();
        for (Integer nodeId : computeNodeIds) {
            computeNodes.add(new ComputeNode(nodeId));
        }

        int newNodesCnt = numComputeNodes - computeNodeIds.size();
        for (int i = 0; i < newNodesCnt; i++) {
            // Find the smallest number not in computeShardIds
            int smallestShardId = 0;
            while (computeNodeIds.contains(smallestShardId)) {
                smallestShardId++;
            }
            computeNodes.add(new ComputeNode(smallestShardId));
            computeNodeIds.add(smallestShardId);
        }

        // Done initializing compute nodes and index shards

        // Try to place index shards to a given set of compute nodes
        for (Map.Entry<Index, List<Integer>> entry : indexComputeNodeIdsMap.entrySet()) {
            Index index = entry.getKey();
            List<Integer> assignedNodeIds = entry.getValue();

            for (Integer nodeId : assignedNodeIds) {
                IndexShard indexShard = indexShards.stream()
                    .filter(shard -> shard.getIndex().equals(index))
                    .findFirst()
                    .orElse(null);

                ComputeNode computeNode = computeNodes.stream()
                    .filter(node -> node.getNodeId() == nodeId)
                    .findFirst()
                    .orElse(null);

                if (indexShard != null && computeNode != null) {
                    computeNode.addIndexShard(indexShard);
                    indexShards.remove(indexShard);
                }
            }
        }

        // Place remaining index shards to compute nodes in ascending order of storage size
        computeNodes.sort(Comparator.comparingLong(ComputeNode::getStorageSize));
        for (IndexShard indexShard : indexShards) {
            computeNodes.getFirst().addIndexShard(indexShard);
            computeNodes.sort(Comparator.comparingLong(ComputeNode::getStorageSize));
        }

        Map<Index, List<Integer>> newComputeNodeIdsMap = new HashMap<>();
        for (ComputeNode computeNode : computeNodes) {
            for (IndexShard indexShard : computeNode.getIndexShards()) {
                List<Integer> newComputeNodeIds = newComputeNodeIdsMap.computeIfAbsent(
                    indexShard.getIndex(), k -> new ArrayList<>());
                if (!newComputeNodeIds.contains(computeNode.getNodeId())) {
                    newComputeNodeIds.add(computeNode.getNodeId());
                }
            }
        }

        // Find indexes that compute node mapping is changed
        long currentUnixEpoch = getCurrentUnixEpoch();
        for (Index index : indexes) {
            List<Integer> newComputeNodeIds = newComputeNodeIdsMap.get(index);
            if (!index.getComputeNodeIds().equals(newComputeNodeIds)) {
                Index needUpdateIndex = new Index();
                needUpdateIndex.setProjectId(index.getProjectId());
                needUpdateIndex.setIndexName(index.getIndexName());
                needUpdateIndex.setComputeNodeIds(newComputeNodeIds);
                needUpdateIndex.setUpdatedAt(currentUnixEpoch);

                // Use try-update because it is possible that the index is being deleted.
                // This will be adjusted at the next reschedule.
                ddbClient.tryUpdateIndex(needUpdateIndex);

                // Launch index builder if it is idle to reflect the changes
                long timeDiff = currentUnixEpoch - index.getWriteLockedAt();
                if (!index.getWriteLocked() ||
                    timeDiff > getIndexBuilderFailureTimeout()) {
                    IndexBuildEvent indexBuildEvent = new IndexBuildEvent();
                    indexBuildEvent.setProjectId(index.getProjectId());
                    indexBuildEvent.setIndexName(index.getIndexName());
                    indexBuildEvent.setShardId(TEMPORARY_SHARD_ID);
                    batchClient.submitIndexBuilderJob(indexBuildEvent,
                        index.getIndexClass(),
                        INDEX_BUILDER_JOB_NAME_PREFIX, INDEX_BUILDER_JOB_QUEUE_NAME
                    );
                }
            }
        }
    }

    private class ComputeNode {

        private final int nodeId;
        private long storageSize;
        private final Set<IndexShard> indexShards;

        public ComputeNode(int nodeId) {
            this.nodeId = nodeId;
            this.storageSize = 0;
            this.indexShards = new HashSet<>();
        }

        public void addIndexShard(IndexShard indexShard) {
            indexShards.add(indexShard);
            storageSize += indexShard.getShardSize();
        }

        public void removeIndexShard(IndexShard indexShard) {
            indexShards.remove(indexShard);
            storageSize -= indexShard.getShardSize();
        }

        public int getNodeId() {
            return nodeId;
        }

        public long getStorageSize() {
            return storageSize;
        }

        public Set<IndexShard> getIndexShards() {
            return indexShards;
        }

        @Override
        public String toString() {
            return "ComputeNode{" +
                "nodeId=" + nodeId +
                ", storageSize=" + storageSize +
                ", indexShards=" + indexShards +
                '}';
        }
    }

    private class IndexShard {

        private final Index index;
        private final long shardSize;

        public IndexShard(Index index, long shardSize) {
            this.index = index;
            this.shardSize = shardSize;
        }

        public Index getIndex() {
            return index;
        }

        public long getShardSize() {
            return shardSize;
        }

        @Override
        public String toString() {
            return "IndexShard{" +
                "index=" + index +
                ", shardSize=" + shardSize +
                '}';
        }
    }

    private boolean deleteIndex(final String projectId, final String indexName)
        throws GlobalExceptionHandler, IOException {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(indexName);

        Index index = ddbClient.getIndexConsistent(projectId, indexName);
        if (index == null) {
            // This index has been deleted by another context
            return true;
        }

        // Check whether it is okay to delete the index
        long timeDiff = getCurrentUnixEpoch() - index.getWriteLockedAt();
        if (index.getWriteLocked() && timeDiff < getIndexBuilderFailureTimeout()) {
            GlobalLogger.info(
                projectId + "." + indexName + " index cannot be deleted since it is write-locked");
            return false;
        }

        // Delete all the files in the index directory
        s3Client.deleteObjectsWithPrefix(INDEX_BUCKET, index.getStorageInfo().getPathPrefix());

        // Add deleted index
        DeletedIndex deletedIndex = new DeletedIndex();
        deletedIndex.setId(UUID.generateUUID());
        deletedIndex.setProjectId(projectId);
        deletedIndex.setIndexName(indexName);
        deletedIndex.setNumShards(index.getNumShards());
        deletedIndex.setDeletedAt(getCurrentUnixEpoch());
        ddbClient.createDeletedIndex(deletedIndex);

        // Delete index metadata
        ddbClient.deleteIndex(projectId, indexName);

        // Delete efs directory for index mutation log
        // TODO: Handle multiple physical shards
        efsClient.deleteDirectory(PathBuilder.buildFsIndexPath(EFS_MOUNT_PATH, index));

        return true;
    }
}


