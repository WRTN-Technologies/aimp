package io.wrtn.dto;

import io.wrtn.model.index.Index;

public class IndexUpdateResponse {

    IndexResponse index;

    public IndexUpdateResponse(final Index index) {
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
        return "IndexUpdateResponse{" +
            "index=" + index +
            '}';
    }

}
