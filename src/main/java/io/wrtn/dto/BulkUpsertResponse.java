package io.wrtn.dto;

public class BulkUpsertResponse {

    private String url;
    String type = "application/json";
    private String httpMethod;
    private String objectKey;
    private long sizeLimitBytes;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public long getSizeLimitBytes() {
        return sizeLimitBytes;
    }

    public void setSizeLimitBytes(long sizeLimitBytes) {
        this.sizeLimitBytes = sizeLimitBytes;
    }

    @Override
    public String toString() {
        return "UploadUrlResponse{" +
            "url='" + url + '\'' +
            ", type='" + type + '\'' +
            ", httpMethod='" + httpMethod + '\'' +
            ", objectKey='" + objectKey + '\'' +
            ", sizeLimitBytes='" + sizeLimitBytes + '\'' +
            '}';
    }
}
