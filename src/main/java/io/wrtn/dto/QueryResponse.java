package io.wrtn.dto;

import io.wrtn.model.document.Document;

import java.util.Arrays;

public class QueryResponse {

    private long took;
    private Float maxScore;
    private long total;
    private Document[] docs;

    public long getTook() {
        return took;
    }

    public void setTook(long took) {
        this.took = took;
    }

    public Float getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Float maxScore) {
        this.maxScore = maxScore;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public Document[] getDocs() {
        return docs;
    }

    public void setDocs(Document[] docs) {
        this.docs = docs;
    }

    @Override
    public String toString() {
        return "QueryResponse{" +
            "took=" + took +
            ", maxScore=" + maxScore +
            ", total=" + total +
            ", docs=" + Arrays.toString(docs) +
            '}';
    }
}
