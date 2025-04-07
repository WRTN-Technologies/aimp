package io.wrtn.dto;

import io.wrtn.model.index.Index;

public class IndexDescribeResponse {

    IndexResponse index;

    public IndexDescribeResponse(final Index index) {
        this.index = new IndexResponse(index);
    }

    public IndexResponse getIndex() {
        return index;
    }

    public void setIndex(IndexResponse index) {
        this.index = index;
    }

    @Override
    public String toString() {
        return "IndexDescribeResponse{" +
            "index=" + index +
            '}';
    }
}
