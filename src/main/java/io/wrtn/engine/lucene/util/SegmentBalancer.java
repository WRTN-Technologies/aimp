package io.wrtn.engine.lucene.util;

import io.wrtn.model.storage.ComputeNode;
import io.wrtn.util.GlobalLogger;
import java.io.IOException;
import java.util.*;
import org.apache.lucene.index.SegmentCommitInfo;

public class SegmentBalancer {

    public static void assignSegmentsToNodes(List<SegmentCommitInfo> segments,
        List<ComputeNode> nodes) {
        // Sort segments by size in descending order
        segments.sort(Comparator.comparingLong(
            s -> {
                try {
                    return -s.sizeInBytes();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));

        // Assign segment to the smallest node
        for (SegmentCommitInfo segment : segments) {
            nodes.stream()
                .min(Comparator.comparingLong(ComputeNode::getTotalSize))
                .ifPresent(node -> {
                    try {
                        node.addSegment(segment);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            });
        }
    }

    public static List<ComputeNode> balanceSegments(
        List<ComputeNode> oldComputeNodes,
        List<Integer> newComputeNodeIds,
        List<SegmentCommitInfo> segments) throws IOException {

        List<ComputeNode> newComputeNodes = new ArrayList<>();
        if (oldComputeNodes.isEmpty()) {
            // If there are no compute nodes in the oldComputeNodes, create a new node.
            // Initially, make one index shard
            assert newComputeNodeIds.size() == 1;

            newComputeNodes.add(new ComputeNode(newComputeNodeIds.getFirst()));
        } else {
            for (ComputeNode oldNode : oldComputeNodes) {
                int nodeId = oldNode.getNodeId();
                if (newComputeNodeIds.contains(nodeId)) {
                    ComputeNode newNode = new ComputeNode(nodeId);
                    for (String segmentId : oldNode.getSegmentIds()) {
                        SegmentCommitInfo segment = segments.stream()
                            .filter(s -> s.info.name.equals(segmentId))
                            .findFirst()
                            .orElse(null);
                        if (segment != null) {
                            newNode.addSegment(segment);
                        }
                    }
                    newComputeNodes.add(newNode);
                }
            }
        }

        // Find unassigned segments
        List<SegmentCommitInfo> unassignedSegments = new ArrayList<>(segments);
        for (ComputeNode node : newComputeNodes) {
            for (SegmentCommitInfo segment : node.getSegments()) {
                unassignedSegments.removeIf(s -> s.info.name.equals(segment.info.name));
            }
        }

        // Add newly created compute nodes
        Set<Integer> newNodeIdSet = new HashSet<>(newComputeNodeIds);
        for (ComputeNode node : newComputeNodes) {
            newNodeIdSet.remove(node.getNodeId());
        }
        for (Integer newNodeId : newNodeIdSet) {
            newComputeNodes.add(new ComputeNode(newNodeId));
        }

        // Assign newly created segments to best-matching compute nodes
        assignSegmentsToNodes(unassignedSegments, newComputeNodes);

        GlobalLogger.info("Assigned new segments to nodes : " + newComputeNodes);

        return newComputeNodes;
    }
}
