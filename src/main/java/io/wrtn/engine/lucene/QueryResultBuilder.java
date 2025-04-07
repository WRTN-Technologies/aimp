package io.wrtn.engine.lucene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.wrtn.util.ValueConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;

import static io.wrtn.engine.lucene.Constants.*;
import static io.wrtn.util.JsonParser.gson;

import org.apache.lucene.index.IndexableField;

public final class QueryResultBuilder {

    public static JsonObject buildDocument(Document luceneDoc, boolean includeVectors,
        String[] fields) {
        JsonObject fullJsonDoc = gson.fromJson(luceneDoc.get(DOC_FIELD_INTERNAL_DOCUMENT),
            JsonObject.class);

        if (fields != null) {
            JsonObject fieldTree = buildFieldTree(fields);
            pruneJsonObject(fullJsonDoc, fieldTree);
        }

        if (includeVectors) {
            addVectorFields(luceneDoc, fullJsonDoc);
        }

        return fullJsonDoc;
    }

    private static JsonObject buildFieldTree(String[] fields) {
        JsonObject root = new JsonObject();

        // Add default field id
        root.add(DOC_FIELD_ID, new JsonObject());

        for (String field : fields) {
            if (field.contains(NESTED_FIELD_DELIMITER)) {
                String[] parts = field.split(NESTED_FIELD_DELIMITER_ESCAPED);
                JsonObject current = root;

                for (String part : parts) {
                    if (!current.has(part)) {
                        current.add(part, new JsonObject());
                    }
                    current = current.getAsJsonObject(part);
                }
            } else {
                if (!root.has(field)) {
                    root.add(field, new JsonObject());
                }
            }
        }

        return root;
    }

    private static void pruneJsonObject(JsonObject fullJsonDoc, JsonObject fieldTree) {
        List<String> fieldsToRemove = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : fullJsonDoc.entrySet()) {
            String key = entry.getKey();

            if (!fieldTree.has(key)) {
                fieldsToRemove.add(key);
            } else if (entry.getValue().isJsonObject()) {
                JsonObject childObject = entry.getValue().getAsJsonObject();
                pruneJsonObject(childObject, fieldTree.getAsJsonObject(key));

                if (childObject.isEmpty()) {
                    fieldsToRemove.add(key);
                }
            }
        }

        for (String fieldToRemove : fieldsToRemove) {
            fullJsonDoc.remove(fieldToRemove);
        }
    }

    private static void addVectorFields(Document luceneDoc, JsonObject jsonDoc) {
        for (IndexableField field : luceneDoc.getFields()) {
            String fieldName = field.name();
            if (fieldName.contains(NESTED_FIELD_DELIMITER) &&
                fieldName.endsWith(VECTOR_VALUE_SUFFIX)) {
                String strippedName = fieldName.substring(0,
                    fieldName.lastIndexOf(NESTED_FIELD_DELIMITER));

                float[] values = ValueConverter.byteArrayToFloatArray(field.binaryValue().bytes);
                JsonArray jsonArray = new JsonArray(values.length);
                for (float value : values) {
                    jsonArray.add(value);
                }

                if (strippedName.contains(NESTED_FIELD_DELIMITER)) {
                    List<String> nestedNames = Arrays.stream(
                        strippedName.split(NESTED_FIELD_DELIMITER_ESCAPED)).toList();
                    JsonObject innerMostObject = jsonDoc;
                    for (int i = 0; i < nestedNames.size() - 1; i++) {
                        String name = nestedNames.get(i);
                        innerMostObject = innerMostObject.getAsJsonObject(name);
                    }
                    innerMostObject.add(nestedNames.getLast(), jsonArray);
                } else {
                    jsonDoc.add(strippedName, jsonArray);
                }
            }
        }
    }
}