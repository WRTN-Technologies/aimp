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
import io.wrtn.model.index.FieldConfig;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import com.google.gson.JsonObject;

public class QueryEvent {

    private String type;
    private String projectId;
    private String indexName;
    private String indexClass;
    private Integer shardId;
    private Map<String, FieldConfig> mappings;
    @JsonSerialize(using = JsonObjectSerializer.class)
    @JsonDeserialize(using = JsonObjectDeserializer.class)
    JsonObject query;
    @JsonSerialize(using = JsonArraySerializer.class)
    @JsonDeserialize(using = JsonArrayDeserializer.class)
    JsonArray sort;
    private String[] fields;
    private boolean trackScores;
    private String[] ids;
    private Integer size;
    private Integer computeNodeId;
    private boolean includeVectors;
    private String metaObjectKey;
    private String metaObjectVersionId;

    public QueryEvent() {
        this.query = new JsonObject();
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getIndexClass() {
        return indexClass;
    }

    public void setIndexClass(String indexClass) {
        this.indexClass = indexClass;
    }

    public int getShardId() {
        return shardId;
    }

    public void setShardId(int shardId) {
        this.shardId = shardId;
    }

    public Map<String, FieldConfig> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, FieldConfig> mappings) {
        this.mappings = mappings;
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

    public void setShardId(Integer shardId) {
        this.shardId = shardId;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Integer getComputeNodeId() {
        return computeNodeId;
    }

    public void setComputeNodeId(Integer computeNodeId) {
        this.computeNodeId = computeNodeId;
    }

    public boolean getIncludeVectors() {
        return includeVectors;
    }

    public void setIncludeVectors(boolean includeVectors) {
        this.includeVectors = includeVectors;
    }

    public String getMetaObjectKey() {
        return metaObjectKey;
    }

    public void setMetaObjectKey(String metaObjectKey) {
        this.metaObjectKey = metaObjectKey;
    }

    public String getMetaObjectVersionId() {
        return metaObjectVersionId;
    }

    public void setMetaObjectVersionId(String metaObjectVersionId) {
        this.metaObjectVersionId = metaObjectVersionId;
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

    @Override
    public String toString() {
        return "QueryEvent{" +
            "type='" + type + '\'' +
            ", projectId='" + projectId + '\'' +
            ", indexName='" + indexName + '\'' +
            ", indexClass='" + indexClass + '\'' +
            ", shardId=" + shardId +
            ", mappings=" + mappings +
            ", query=" + query +
            ", sort=" + sort +
            ", fields=" + Arrays.toString(fields) +
            ", trackScores=" + trackScores +
            ", ids=" + Arrays.toString(ids) +
            ", size=" + size +
            ", computeNodeId=" + computeNodeId +
            ", includeVectors=" + includeVectors +
            ", metaObjectKey='" + metaObjectKey + '\'' +
            ", metaObjectVersionId='" + metaObjectVersionId + '\'' +
            '}';
    }
}
