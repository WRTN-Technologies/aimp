package io.wrtn.model.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.lucene.index.SegmentInfos;

public class StorageMetadata {

    private List<String> prefixes;
    private ConcurrentHashMap<String, File> fileMap;
    private long totalSize;
    private long dataUpdatedAt;
    private List<Integer> computeNodeIds;
    private List<ComputeNode> computeNodes;
    private List<String> segmentIds;
    private transient SegmentInfos segmentInfos;

    public StorageMetadata() {}

    public StorageMetadata(String prefix, List<Integer> computeNodeIds) {
        this.prefixes = new ArrayList<>();
        this.prefixes.add(prefix);
        this.fileMap = new ConcurrentHashMap<>();
        this.totalSize = 0;
        this.dataUpdatedAt = 0;
        this.computeNodeIds = computeNodeIds;
    }

    public StorageMetadata(StorageMetadata srcMeta) {
        this.prefixes = srcMeta.getPrefixes();
        this.fileMap = new ConcurrentHashMap<>();
        this.dataUpdatedAt = srcMeta.getDataUpdatedAt();
        this.totalSize = srcMeta.getTotalSize();
        this.computeNodeIds = srcMeta.getComputeNodeIds();
        this.segmentIds = srcMeta.getSegmentIds();
    }

    public StorageMetadata(StorageMetadata srcMeta, SegmentInfos segmentInfos) {
        this.prefixes = srcMeta.getPrefixes();
        this.fileMap = new ConcurrentHashMap<>();
        this.dataUpdatedAt = srcMeta.getDataUpdatedAt();
        this.segmentInfos = segmentInfos;
        this.totalSize = 0;
        this.segmentIds = new ArrayList<>();
    }

    public void setPrefixes(List<String> prefixes) {
        this.prefixes = prefixes;
    }

    public List<String> getPrefixes() {
        return prefixes;
    }

    public void addPrefix(String prefix) {
        prefixes.add(prefix);
    }

    public String getCurrentPrefix() {
        return prefixes.getLast();
    }

    public Integer getCurrentPrefixId() {
        return prefixes.size() - 1;
    }

    public void setFileMap(ConcurrentHashMap<String, File> fileMap) {
        this.fileMap = fileMap;
    }

    public Map<String, File> getFileMap() {
        return fileMap;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public long getDataUpdatedAt() {
        return dataUpdatedAt;
    }

    public void setDataUpdatedAt(long dataUpdatedAt) {
        this.dataUpdatedAt = dataUpdatedAt;
    }

    public List<Integer> getComputeNodeIds() {
        return computeNodeIds;
    }

    public void setComputeNodeIds(List<Integer> computeNodeIds) {
        this.computeNodeIds = computeNodeIds;
    }

    public List<ComputeNode> getComputeNodes() {
        return computeNodes;
    }

    public void setComputeNodes(List<ComputeNode> computeNodes) {
        this.computeNodes = computeNodes;
    }

    public SegmentInfos getSegmentInfos() {
        return segmentInfos;
    }

    public void setSegmentIds(List<String> segmentIds) {
        this.segmentIds = segmentIds;
    }

    public List<String> getSegmentIds() {
        return segmentIds;
    }

    @Override
    public String toString() {
        return "StorageMetadata{" +
            "prefixes=" + prefixes +
            "fileMap=" + fileMap +
            ", totalSize=" + totalSize +
            ", dataUpdatedAt=" + dataUpdatedAt +
            ", computeNodeIds=" + computeNodeIds +
            ", segmentInfos=" + segmentInfos +
            ", segmentIds=" + segmentIds +
            '}';
    }
}
