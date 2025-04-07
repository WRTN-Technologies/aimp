package io.wrtn.dto;

import java.util.Arrays;

public class FetchRequest {

    private String[] ids;
    private boolean consistentRead;
    private boolean includeVectors;
    private String[] fields;

    public String[] getIds() {
        return ids;
    }

    public void setIds(String[] ids) {
        this.ids = ids;
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

    public String[] getFields() {
        return fields;
    }

    public void setFields(String[] fields) {
        this.fields = fields;
    }

    @Override
    public String toString() {
        return "FetchRequest{" +
            "ids=" + Arrays.toString(ids) +
            ", consistentRead=" + consistentRead +
            ", includeVectors=" + includeVectors +
            ", fields=" + Arrays.toString(fields) +
            '}';
    }
}
