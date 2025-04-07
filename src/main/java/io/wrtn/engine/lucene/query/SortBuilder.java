package io.wrtn.engine.lucene.query;

import static io.wrtn.engine.lucene.Constants.DATA_TYPE_DATETIME;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_DOUBLE;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_KEYWORD;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_LONG;
import static io.wrtn.engine.lucene.Constants.DOC_FIELD_ID;
import static io.wrtn.engine.lucene.Constants.NESTED_FIELD_DELIMITER;
import static io.wrtn.engine.lucene.Constants.NESTED_FIELD_DELIMITER_ESCAPED;
import static io.wrtn.engine.lucene.Constants.SORT_DIRECTION_ASC;
import static io.wrtn.engine.lucene.Constants.SORT_DIRECTION_DESC;
import static io.wrtn.engine.lucene.Constants.SORT_FIELD_SUFFIX;
import static io.wrtn.engine.lucene.Constants.SUPPORTED_SORT_FIELDS;

import com.google.gson.JsonArray;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;
import java.util.Map.Entry;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SortBuilder {

    public static Sort build(JsonArray sortArray, Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler {

        if (sortArray.isJsonNull() || sortArray.isEmpty()) {
            throw new GlobalExceptionHandler("Sort Array cannot be null or empty",
                StatusCode.INVALID_INPUT_VALUE);
        }

        List<SortField> sortFields = new ArrayList<>();

        for (JsonElement sortElement : sortArray) {
            if (!sortElement.isJsonObject()) {
                throw new IllegalArgumentException("Sort element must be an object");
            }

            Set<Entry<String, JsonElement>> entries = getValidatedEntries(sortElement);
            for (Map.Entry<String, JsonElement> entry : entries) {
                String fieldName = entry.getKey();
                String direction = getValidatedSortDirection(entry);
                String fieldType = getValidatedFieldType(fieldName, mappings);

                SortField sortField = createSortField(fieldName, fieldType, direction);
                sortFields.add(sortField);
            }
            SortField defaultIdSortField = createSortField(DOC_FIELD_ID, DATA_TYPE_KEYWORD,
                false);
            sortFields.add(defaultIdSortField);
        }

        return new Sort(sortFields.toArray(new SortField[0]));
    }

    private static Set<Entry<String, JsonElement>> getValidatedEntries(JsonElement sortElement)
        throws GlobalExceptionHandler {
        if (!sortElement.isJsonObject()) {
            throw new GlobalExceptionHandler("Sort element must be an object",
                StatusCode.INVALID_INPUT_VALUE);
        }
        JsonObject sortObject = sortElement.getAsJsonObject();
        if (sortObject.entrySet().size() > 1) {
            throw new GlobalExceptionHandler("Sort element must have exactly one field",
                StatusCode.INVALID_INPUT_VALUE);
        }

        return sortObject.entrySet();
    }

    private static String getValidatedSortDirection(Map.Entry<String, JsonElement> directionEntry)
        throws GlobalExceptionHandler {
        String direction = directionEntry.getValue().isJsonNull() ? SORT_DIRECTION_ASC
            : directionEntry.getValue().getAsString().toLowerCase();
        if (!direction.equals(SORT_DIRECTION_ASC) && !direction.equals(SORT_DIRECTION_DESC)) {
            throw new GlobalExceptionHandler(
                "Invalid sort direction: " + direction + ". Must be either " + SORT_DIRECTION_ASC
                    + " or " + SORT_DIRECTION_DESC, StatusCode.INVALID_INPUT_VALUE);
        }
        return direction;
    }

    private static String getValidatedFieldType(String fieldName,
        Map<String, FieldConfig> mappings) throws GlobalExceptionHandler {

        String[] fieldParts = fieldName.split(NESTED_FIELD_DELIMITER_ESCAPED);
        FieldConfig currentConfig;
        Map<String, FieldConfig> currentMappings = mappings;

        for (int i = 0; i < fieldParts.length - 1; i++) {
            String part = fieldParts[i];
            if (!currentMappings.containsKey(part)) {
                throw new GlobalExceptionHandler(
                    "Field " + fieldName + " does not exist in mappings",
                    StatusCode.INVALID_INPUT_VALUE);
            }
            currentConfig = currentMappings.get(part);
            currentMappings = currentConfig.getObjectMapping();

            if (currentMappings == null) {
                throw new GlobalExceptionHandler(
                    "Field " + fieldName + " is not a nested field",
                    StatusCode.INVALID_INPUT_VALUE);
            }
        }

        String lastPart = fieldParts[fieldParts.length - 1];
        if (!currentMappings.containsKey(lastPart)) {
            throw new GlobalExceptionHandler(
                "Field " + fieldName + " does not exist in mappings",
                StatusCode.INVALID_INPUT_VALUE);
        }

        return currentMappings.get(lastPart).getType();
    }

    private static SortField createSortField(String fieldName, String fieldType, String direction)
        throws GlobalExceptionHandler {
        return createSortField(fieldName, fieldType, direction.equals(SORT_DIRECTION_DESC));
    }

    private static SortField createSortField(String fieldName, String fieldType, boolean reverse)
        throws GlobalExceptionHandler {

        return switch (fieldType.toLowerCase()) {
            case DATA_TYPE_LONG, DATA_TYPE_DATETIME ->
                new SortField(fieldName + NESTED_FIELD_DELIMITER + SORT_FIELD_SUFFIX,
                    SortField.Type.LONG, reverse);
            case DATA_TYPE_DOUBLE ->
                new SortField(fieldName + NESTED_FIELD_DELIMITER + SORT_FIELD_SUFFIX,
                    SortField.Type.DOUBLE, reverse);
            case DATA_TYPE_KEYWORD ->
                new SortField(fieldName + NESTED_FIELD_DELIMITER + SORT_FIELD_SUFFIX,
                    SortField.Type.STRING, reverse);
            default -> throw new GlobalExceptionHandler(
                "Unsupported field type for sorting: " + fieldType + ". Supported types: "
                    + SUPPORTED_SORT_FIELDS, StatusCode.INVALID_INPUT_VALUE);
        };
    }
}