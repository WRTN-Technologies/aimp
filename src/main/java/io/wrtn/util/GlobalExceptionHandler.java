package io.wrtn.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class GlobalExceptionHandler extends Exception implements
    JsonSerializer<GlobalExceptionHandler>,
    JsonDeserializer<GlobalExceptionHandler> {

    private final StatusCode statusCode;

    public GlobalExceptionHandler() {
        this.statusCode = StatusCode.SERVER_ERROR;
    }

    public GlobalExceptionHandler(StatusCode statusCode) {
        super(statusCode.getMessage());
        this.statusCode = statusCode;
    }

    public GlobalExceptionHandler(String message, StatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public StatusCode getErrorCode() {
        return this.statusCode;
    }

    @Override
    public JsonElement serialize(GlobalExceptionHandler src, Type typeOfSrc,
        JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("message", src.getMessage());
        jsonObject.addProperty("statusCode", src.getErrorCode().name());
        return jsonObject;
    }

    @Override
    public GlobalExceptionHandler deserialize(JsonElement json, Type typeOfT,
        JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String message = jsonObject.get("message").getAsString();
        StatusCode statusCode = StatusCode.valueOf(jsonObject.get("statusCode").getAsString());
        return new GlobalExceptionHandler(message, statusCode);
    }
}
