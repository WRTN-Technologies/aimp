package io.wrtn.model.document;

import static io.wrtn.util.JsonParser.gson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import com.google.gson.JsonObject;

import java.io.IOException;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class Document {

    private String index;
    private Float score;
    @JsonSerialize(using = JsonObjectSerializer.class)
    @JsonDeserialize(using = JsonObjectDeserializer.class)
    JsonObject doc;

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public JsonObject getDoc() {
        return doc;
    }

    public void setDoc(JsonObject doc) {
        this.doc = doc;
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
            return gson.fromJson(p.getText(), JsonObject.class);
        }
    }

    @Override
    public String toString() {
        return "Document{" +
            "index='" + index + '\'' +
            ", score=" + score +
            ", doc=" + doc +
            '}';
    }
}
