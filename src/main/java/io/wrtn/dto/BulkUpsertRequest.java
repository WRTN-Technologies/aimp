package io.wrtn.dto;

public class BulkUpsertRequest {

    private String objectKey;

    // Default type is application/json
    String type = "application/json";

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "BatchUpsertRequest{" +
            "objectKey='" + objectKey + '\'' +
            ", type='" + type + '\'' +
            '}';
    }
}
