package io.wrtn.model.event;

import static io.wrtn.util.JsonParser.gson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.wrtn.model.index.Index;
import io.wrtn.model.storage.StorageMetadata;
import java.io.IOException;
import java.util.Arrays;

public class RefreshEvent {

    private String type;
    private Index index;
    @JsonSerialize(using = JsonObjectSerializer.class)
    @JsonDeserialize(using = JsonObjectDeserializer.class)
    JsonObject query;
    @JsonSerialize(using = JsonArraySerializer.class)
    @JsonDeserialize(using = JsonArrayDeserializer.class)
    JsonArray sort;
    String[] fields;
    private boolean trackScores;
    private String[] ids;
    private Integer size;
    private boolean includeVectors;
    private StorageMetadata storageMetadata;

    public RefreshEvent() {
        this.query = new JsonObject();
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

    // Serializer
    public static class JsonObjectSerializer extends JsonSerializer<JsonObject> {

        @Override
        public void serialize(JsonObject value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
            gen.writeRawValue(value.toString());
        }
    }

    // Deserializer
    public static class JsonObjectDeserializer extends JsonDeserializer<JsonObject> {

        @Override
        public JsonObject deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
            return gson.fromJson(p.readValueAsTree().toString(), JsonObject.class);
        }
    }

    // Serializer
    public static class JsonArraySerializer extends JsonSerializer<JsonArray> {

        @Override
        public void serialize(JsonArray value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
            gen.writeRawValue(value.toString());
        }
    }

    // Deserializer
    public static class JsonArrayDeserializer extends JsonDeserializer<JsonArray> {

        @Override
        public JsonArray deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
            return gson.fromJson(p.readValueAsTree().toString(), JsonArray.class);
        }
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
