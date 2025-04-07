package io.wrtn.dto;

import io.wrtn.model.document.Document;

import java.util.Arrays;

public class FetchResponse {

    private int total;
    private long took;
    private Document[] docs;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public long getTook() {
        return took;
    }

    public void setTook(long took) {
        this.took = took;
    }

    public Document[] getDocs() {
        return docs;
    }

    public void setDocs(Document[] docs) {
        this.docs = docs;
    }

    @Override
    public String toString() {
        return "FetchResponse{" +
            "total=" + total +
            ", took=" + took +
            ", _docs=" + Arrays.toString(docs) +
            '}';
    }
}
