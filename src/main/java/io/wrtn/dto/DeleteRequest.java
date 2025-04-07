package io.wrtn.dto;

import com.google.gson.JsonObject;

import java.util.List;

public class DeleteRequest {

    private final List<String> ids;
    private final JsonObject filter;

    public DeleteRequest(List<String> ids, JsonObject filter) {
        this.ids = ids;
        this.filter = filter;
    }

    public List<String> getIds() {
        return ids;
    }

    public JsonObject getFilter() {
        return filter;
    }

    @Override
    public String toString() {
        return "DeleteRequest{" +
            "ids=" + ids +
            ", filter=" + filter +
            '}';
    }
}
