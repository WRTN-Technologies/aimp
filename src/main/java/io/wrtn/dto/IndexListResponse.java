package io.wrtn.dto;

import io.wrtn.model.index.Index;

import java.util.ArrayList;
import java.util.List;

public class IndexListResponse {

    List<IndexResponse> indexes;

    public IndexListResponse(List<Index> indexes) {
        this.indexes = new ArrayList<>();
        for (Index index : indexes) {
            this.indexes.add(new IndexResponse(index));
        }
    }

    public List<IndexResponse> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexResponse> indexes) {
        this.indexes = indexes;
    }

    @Override
    public String toString() {
        return "IndexListResponse{" +
            "indexes=" + indexes +
            '}';
    }
}
