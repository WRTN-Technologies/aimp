package io.wrtn.model.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.lucene.index.SegmentCommitInfo;

public class ComputeNode {

    private Integer nodeId;
    private List<String> segmentIds;
    private transient long totalSize;
    private transient List<SegmentCommitInfo> segments;

    public ComputeNode() {}

    public ComputeNode(Integer nodeId) {
        this.nodeId = nodeId;
        this.segmentIds = new ArrayList<>();
        this.totalSize = 0;
        this.segments = new ArrayList<>();
    }

    public ComputeNode(Integer nodeId, List<String> segmentIds) {
        this.nodeId = nodeId;
        this.segmentIds = segmentIds;
        this.totalSize = 0;
        this.segments = new ArrayList<>();
    }

    public Integer getNodeId() {
        return nodeId;
    }

    public void setNodeId(Integer nodeId) {
        this.nodeId = nodeId;
    }

    public List<String> getSegmentIds() {
        return segmentIds;
    }

    public void setSegmentIds(List<String> segmentIds) {
        this.segmentIds = segmentIds;
    }

    public void addSegment(SegmentCommitInfo segment) throws IOException {
        segments.add(segment);
        totalSize += segment.sizeInBytes();
        segments.sort(Comparator.comparingLong(s -> {
            try {
                return s.sizeInBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
        segmentIds.add(segment.info.name);
    }

    public void removeSegment(SegmentCommitInfo segment) throws IOException {
        segmentIds.remove(segment.info.name);
        segments.remove(segment);
        totalSize -= segment.sizeInBytes();
    }

    public List<SegmentCommitInfo> getSegments() {
        return segments;
    }

    public void setSegments(List<SegmentCommitInfo> segments) {
        this.segments = segments;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    @Override
    public String toString() {
        return "ComputeNode{" +
            "nodeId=" + nodeId +
            ", segmentIds=" + segmentIds +
            ", totalSize=" + totalSize +
            '}';
    }
}
