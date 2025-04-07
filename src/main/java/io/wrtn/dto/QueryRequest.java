package io.wrtn.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;

public class QueryRequest {

    private Integer size;
    private JsonObject query;
    private JsonArray sort;
    private String[] fields;
    private boolean trackScores;
    private boolean consistentRead;
    private boolean includeVectors;

    public Integer getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public JsonObject getQuery() {
        return query;
    }

    public void setQuery(JsonObject query) {
        this.query = query;
    }

    public JsonArray getSort() {
        return sort;
    }

    public void setSort(JsonArray sort) {
        this.sort = sort;
    }

    public String[] getFields() {
        return fields;
    }

    public void setFields(String[] fields) {
        this.fields = fields;
    }

    public boolean getTrackScores() {
        return trackScores;
    }

    public void setTrackScores(boolean trackScores) {
        this.trackScores = trackScores;
    }

    public boolean getConsistentRead() {
        return consistentRead;
    }

    public void setConsistentRead(boolean consistentRead) {
        this.consistentRead = consistentRead;
    }

    public boolean getIncludeVectors() {
        return includeVectors;
    }

    public void setIncludeVectors(boolean includeVectors) {
        this.includeVectors = includeVectors;
    }

    @Override
    public String toString() {
        return "QueryRequest{" +
            "size=" + size +
            ", query=" + query +
            ", sort=" + sort +
            ", fields=" + Arrays.toString(fields) +
            ", trackScores=" + trackScores +
            ", consistentRead=" + consistentRead +
            ", includeVectors=" + includeVectors +
            '}';
    }
}
