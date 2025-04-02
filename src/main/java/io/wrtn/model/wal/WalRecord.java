package io.wrtn.model.wal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

public class WalRecord {

    private String id;
    private String type;
    private String projectId;
    private String indexName;
    private Integer shardId;
    private Long seqNo;
    private List<Map<String, JsonElement>> documents;
    private List<String> ids;
    private JsonObject filter = new JsonObject();
    private Boolean s3Payload;
    private Long payloadBytes;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public int getShardId() {
        return shardId;
    }

    public void setShardId(int shardId) {
        this.shardId = shardId;
    }

    public Long getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Long seqNo) {
        this.seqNo = seqNo;
    }

    public List<Map<String, JsonElement>> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Map<String, JsonElement>> documents) {
        this.documents = documents;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public JsonObject getFilter() {
        return filter;
    }

    public void setFilter(JsonObject filter) {
        this.filter = filter;
    }

    public boolean getS3Payload() {
        return s3Payload;
    }

    public void setS3Payload(boolean s3Payload) {
        this.s3Payload = s3Payload;
    }

    public Long getPayloadBytes() {
        return payloadBytes;
    }

    public void setPayloadBytes(long payloadBytes) {
        this.payloadBytes = payloadBytes;
    }

    @Override
    public String toString() {
        return "WalRecord{" +
            "id='" + id + '\'' +
            ", type='" + type + '\'' +
            ", projectId='" + projectId + '\'' +
            ", indexName='" + indexName + '\'' +
            ", shardId=" + shardId +
            ", seqNo=" + seqNo +
            ", documents=" + documents +
            ", ids=" + ids +
            ", filter=" + filter +
            ", s3Payload=" + s3Payload +
            ", payloadBytes=" + payloadBytes +
            '}';
    }
}
