package io.wrtn.model.index;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@DynamoDbBean
public class StorageInfo {

    private String pathPrefix;
    private long size;

    @DynamoDbAttribute("pathPrefix")
    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    @DynamoDbAttribute("size")
    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "StorageInfo{" +
            ", pathPrefix='" + pathPrefix + '\'' +
            ", storageSize=" + size +
            '}';
    }
}
