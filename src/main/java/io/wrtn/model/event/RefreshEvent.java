package io.wrtn.model.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.wrtn.model.index.Index;
import io.wrtn.model.storage.StorageMetadata;
import java.util.Arrays;

public class RefreshEvent {

    private String type;
    private Index index;
    JsonObject query;
    JsonArray sort;
    String[] fields;
    private boolean trackScores;
    private String[] ids;
    private Integer size;
    private boolean includeVectors;
    private StorageMetadata storageMetadata;

    public RefreshEvent() {
        this.query = new JsonObject();
        this.sort = new JsonArray();
    }

    public Index getIndex() {
        return index;
    }

    public void setIndex(Index index) {
        this.index = index;
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
        if (sort != null) {
            this.sort = sort;
        }
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String[] getIds() {
        return ids;
    }

    public void setIds(String[] ids) {
        this.ids = ids;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public boolean getIncludeVectors() {
        return includeVectors;
    }

    public void setIncludeVectors(boolean includeVectors) {
        this.includeVectors = includeVectors;
    }

    public StorageMetadata getStorageMetadata() {
        return storageMetadata;
    }

    public void setStorageMetadata(StorageMetadata storageMetadata) {
        this.storageMetadata = storageMetadata;
    }

    @Override
    public String toString() {
        return "RefreshEvent{" +
            "type='" + type + '\'' +
            ", index=" + index +
            ", query=" + query +
            ", sort=" + sort +
            ", fields=" + Arrays.toString(fields) +
            ", trackScores=" + trackScores +
            ", ids=" + Arrays.toString(ids) +
            ", size=" + size +
            ", includeVectors=" + includeVectors +
            ", storageMetadata=" + storageMetadata +
            '}';
    }
}
