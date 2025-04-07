package io.wrtn.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.wrtn.model.document.Document;
import io.wrtn.model.document.RefreshedDocs;
import org.apache.commons.text.StringEscapeUtils;

public final class JsonParser {

    public static final Gson gson = new GsonBuilder().serializeNulls().create();

    public static final Gson exceptionGson = new GsonBuilder()
        .registerTypeAdapter(GlobalExceptionHandler.class, new GlobalExceptionHandler())
        .create();

    public static Document[] parseDocuments(String json) {
        String jsonString = StringEscapeUtils.unescapeJson(json);
        return gson.fromJson(jsonString.substring(1, jsonString.length() - 1),
            Document[].class);

    }

    public static RefreshedDocs parseRefreshedDocs(String json) {
        String jsonString = StringEscapeUtils.unescapeJson(json);
        return gson.fromJson(jsonString.substring(1, jsonString.length() - 1),
            RefreshedDocs.class);
    }

    public static <T> T parseRequestBody(String body, Class<T> classOfT)
        throws GlobalExceptionHandler {
        try {
            return gson.fromJson(body, TypeToken.get(classOfT));
        } catch (JsonSyntaxException e) {
            throw new GlobalExceptionHandler("Invalid JSON body", StatusCode.BAD_REQUEST);
        }
    }
}
