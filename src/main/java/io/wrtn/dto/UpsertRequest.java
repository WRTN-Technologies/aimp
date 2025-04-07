package io.wrtn.dto;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

public class UpsertRequest {

    List<Map<String, JsonElement>> documents;

    public List<Map<String, JsonElement>> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Map<String, JsonElement>> documents) {
        this.documents = documents;
    }

    @Override
    public String toString() {
        return "UpsertRequest{" +
            "documents=" + documents +
            '}';
    }
}
