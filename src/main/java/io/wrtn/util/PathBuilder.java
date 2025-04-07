package io.wrtn.util;

import static io.wrtn.engine.lucene.Constants.STORAGE_META_FILE_NAME;
import static io.wrtn.util.Constants.Config.QUERY_EXECUTOR_BASE_NAME_IA;
import static io.wrtn.util.Constants.Config.QUERY_EXECUTOR_BASE_NAME_STD;
import static io.wrtn.util.Constants.IndexClass.INDEX_CLASS_IA;
import static io.wrtn.util.Constants.IndexClass.INDEX_CLASS_STD;

import io.wrtn.model.index.Index;
import io.wrtn.model.storage.File;
import io.wrtn.model.storage.StorageMetadata;

public final class PathBuilder {

    public static String buildStorageProjectPath(String projectId) {
        return projectId + "/";
    }

    public static String buildStorageIndexPath(String projectId, String indexName) {
        return buildStorageProjectPath(projectId) + indexName + "/";
    }

    public static String buildStorageShardPath(String projectId, String indexName, int shardId) {
        return buildStorageIndexPath(projectId, indexName) + shardId + "/";
    }

    public static String buildStorageShardPath(Index index, int shardId) {
        return buildStorageIndexPath(index.getProjectId(), index.getIndexName()) + shardId + "/";
    }

    public static String buildStorageKeyPath(String projectId, String indexName, int shardId,
        String name) {
        return buildStorageShardPath(projectId, indexName, shardId) + name;
    }

    public static String buildStorageKeyPath(Index index, int shardId, String name) {
        return buildStorageShardPath(index.getProjectId(), index.getIndexName(), shardId) + name;
    }

    public static String buildFsProjectPath(final String root, final String projectId) {
        return root + "/" + projectId;
    }

    public static String buildFsIndexPath(final String root, final String projectId,
        final String indexName) {
        return buildFsProjectPath(root, projectId) + "/" + indexName;
    }

    public static String buildFsIndexPath(final String root, final Index index) {
        return buildFsProjectPath(root, index.getProjectId()) + "/" + index.getIndexName();
    }

    public static String buildFsShardPath(final String root, final String projectId,
        final String indexName, final int shardId) {
        return buildFsIndexPath(root, projectId, indexName) + "/" + shardId + "/";
    }

    public static String buildFsShardPath(final String root, final Index index, final int shardId) {
        return buildFsIndexPath(root, index.getProjectId(), index.getIndexName()) + "/" + shardId
            + "/";
    }

    public static String buildFsWalPath(final String root, final Index index, final int shardId) {
        return buildFsShardPath(root, index, shardId) + "wal/";
    }

    public static String buildFsWalPath(final String root, final String projectId,
        final String indexName, final int shardId) {
        return buildFsShardPath(root, projectId, indexName, shardId) + "wal/";
    }

    public static String buildFsCachePath(final String root, final Index index, final int shardId) {
        return buildFsShardPath(root, index, shardId) + "cache/";
    }

    public static String buildFsCachePath(final String root, final String projectId,
        final String indexName, final int shardId) {
        return buildFsShardPath(root, projectId, indexName, shardId) + "cache/";
    }

    public static String buildFsBufferPath(final String root, final String projectId,
        final String indexName, final int shardId) {
        return buildFsShardPath(root, projectId, indexName, shardId) + "buffer/";
    }

    public static String buildFsBufferPath(final String root, final Index index,
        final int shardId) {
        return buildFsShardPath(root, index, shardId) + "buffer/";
    }

    public static String buildFsBufferFilePath(final String root, final String projectId,
        final String indexName, final int shardId, final String name) {
        return buildFsShardPath(root, projectId, indexName, shardId) + "buffer/" + name;
    }

    public static String buildFsFilePath(final String root, final String projectId,
        final String indexName, final int shardId, final String fileName) {
        return buildFsShardPath(root, projectId, indexName, shardId) + "/" + fileName;
    }

    public static String buildFsFilePath(final String root, final Index index, final int shardId,
        final String fileName) {
        return buildFsShardPath(root, index.getProjectId(), index.getIndexName(), shardId) + "/"
            + fileName;
    }

    public static String buildFsWalFilePath(final String root, final Index index, final int shardId,
        final String fileName) {
        return buildFsShardPath(root, index.getProjectId(), index.getIndexName(), shardId) + "/wal/"
            + fileName;
    }

    public static String buildFsPartitionPath(final String root, final String projectId,
        final String indexName, final int shardId, final int partitionId) {
        return buildFsIndexPath(root, projectId, indexName) + "/" + shardId + "/" + partitionId
            + "/";
    }

    public static String buildQueryExecutorName(int computeShardId, String indexClass) {
        if (indexClass.equals(INDEX_CLASS_STD)) {
            return String.format("%s-%s", QUERY_EXECUTOR_BASE_NAME_STD, computeShardId);
        } else if (indexClass.equals(INDEX_CLASS_IA)) {
            return String.format("%s-%s", QUERY_EXECUTOR_BASE_NAME_IA, computeShardId);
        } else {
            throw new IllegalArgumentException("Invalid index class: " + indexClass);
        }
    }

    public static String buildMetaKey(Index index, int shardId) {
        return buildStorageShardPath(index.getProjectId(), index.getIndexName(), shardId)
            + STORAGE_META_FILE_NAME;
    }

    public static String buildMetaKey(String prefix) {
        return prefix + STORAGE_META_FILE_NAME;
    }

    public static String buildMetaKey(String projectId, String indexName, int shardId) {
        return buildStorageShardPath(projectId, indexName, shardId) + STORAGE_META_FILE_NAME;
    }

    public static String buildPartitionMetaKeyForWrite(StorageMetadata meta, int partitionIndex) {
        return meta.getCurrentPrefix() + partitionIndex + "-" + STORAGE_META_FILE_NAME;
    }

    public static String buildPartitionMetaName(int partitionIndex) {
        return partitionIndex + "-" + STORAGE_META_FILE_NAME;
    }

    public static String buildObjectKeyForRead(StorageMetadata meta, String name) {
        File file = meta.getFileMap().get(name);
        return meta.getPrefixes().get(file.getPrefixId()) + file.getName();
    }

    public static String buildObjectKeyForWrite(StorageMetadata meta, String name) {
        return meta.getCurrentPrefix() + name;
    }

}
