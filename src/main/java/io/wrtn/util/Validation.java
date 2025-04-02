package io.wrtn.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import io.wrtn.dto.IndexCreateRequest;
import io.wrtn.dto.ProjectCreateRequest;
import io.wrtn.dto.ProjectUpdateRequest;
import io.wrtn.infra.aws.SecretsManager;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import io.wrtn.model.index.FieldConfig;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static io.wrtn.engine.lucene.Constants.*;
import static io.wrtn.infra.aws.Constants.ApiGateway.APPLICATION_JSON;
import static io.wrtn.infra.aws.Constants.ApiGateway.CONTENT_TYPE;
import static io.wrtn.infra.aws.Constants.ApiGateway.CONTENT_TYPE_LOWER_CASE;
import static io.wrtn.util.Constants.Config.ADMIN_API_KEY_SECRET_NAME;
import static io.wrtn.util.Constants.IndexClass.INDEX_CLASS_DEFAULT;
import static io.wrtn.util.Constants.IndexClass.INDEX_CLASS_IA;
import static io.wrtn.util.Constants.IndexClass.INDEX_CLASS_STD;
import static io.wrtn.util.JsonParser.gson;
import static io.wrtn.util.TimeUtil.stringToOffsetDateTime;

public final class Validation {

    private final SecretsManager secretsManager;

    public Validation(SecretsManager secretsManager) {
        this.secretsManager = secretsManager;
    }

    private static final Map<String, Boolean> supportedFieldTypes = new HashMap<>(
        Map.ofEntries(
            new AbstractMap.SimpleEntry<>(DATA_TYPE_TEXT, true),
            new AbstractMap.SimpleEntry<>(DATA_TYPE_KEYWORD, true),
            new AbstractMap.SimpleEntry<>(DATA_TYPE_LONG, true),
            new AbstractMap.SimpleEntry<>(DATA_TYPE_DOUBLE, true),
            new AbstractMap.SimpleEntry<>(DATA_TYPE_BOOLEAN, true),
            new AbstractMap.SimpleEntry<>(DATA_TYPE_VECTOR, true),
            new AbstractMap.SimpleEntry<>(DATA_TYPE_DATETIME, true),
            new AbstractMap.SimpleEntry<>(DATA_TYPE_OBJECT, true)));
    private static final Map<String, Boolean> supportedAnalyzerTypes = new HashMap<>(
        Map.ofEntries(
            new AbstractMap.SimpleEntry<>(ANALYZER_TYPE_ENGLISH, true),
            new AbstractMap.SimpleEntry<>(ANALYZER_TYPE_KOREAN, true),
            new AbstractMap.SimpleEntry<>(ANALYZER_TYPE_JAPANESE, true),
            new AbstractMap.SimpleEntry<>(ANALYZER_TYPE_STANDARD, true)));
    private static final Map<String, Boolean> supportedSimilarityMetrics = new HashMap<>(
        Map.ofEntries(
            new AbstractMap.SimpleEntry<>(VECTOR_SIMILARITY_COSINE, true),
            new AbstractMap.SimpleEntry<>(VECTOR_SIMILARITY_DOT_PRODUCT, true),
            new AbstractMap.SimpleEntry<>(VECTOR_SIMILARITY_L2_NORM, true),
            new AbstractMap.SimpleEntry<>(VECTOR_SIMILARITY_MAX_INNER_PRODUCT, true)));

    public static final Type gsonMapType = new TypeToken<Map<String, JsonElement>>() {
    }.getType();

    private void validateTextType(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        List<String> analyzers = fieldConfig.getAnalyzers();
        if (analyzers == null) {
            fieldConfig.setAnalyzers(Collections.singletonList(ANALYZER_TYPE_DEFAULT));
        } else {
            for (String analyzer : analyzers) {
                if (supportedAnalyzerTypes.get(analyzer.toLowerCase()) == null) {
                    throw new GlobalExceptionHandler(
                        analyzer + " is not a supported analyzer type"
                            + "supported analyzer types are "
                            + supportedAnalyzerTypes.keySet(),
                        StatusCode.INVALID_INPUT_VALUE);
                }
            }
        }
    }

    private void validateVectorType(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        Integer dimensions = fieldConfig.getDimensions();
        if (dimensions == null) {
            throw new GlobalExceptionHandler("Dimensions is required field",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (dimensions > MAX_DIMS_COUNT || dimensions <= 16) {
            throw new GlobalExceptionHandler("Dimensions must be between 1 and " + MAX_DIMS_COUNT,
                StatusCode.INVALID_INPUT_VALUE);
        }

        String similarity = fieldConfig.getSimilarity();
        if (similarity == null) {
            fieldConfig.setSimilarity(VECTOR_SIMILARITY_DEFAULT);
        } else if (supportedSimilarityMetrics.get(similarity.toLowerCase()) == null) {
            throw new GlobalExceptionHandler(
                similarity + " is not a supported similarity metric"
                    + "supported similarity metrics are "
                    + supportedSimilarityMetrics.keySet(),
                StatusCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateSimpleType(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        if (fieldConfig.getAnalyzers() != null) {
            throw new GlobalExceptionHandler(
                "Analyzers are not allowed for " + fieldConfig.getType() + " field type",
                StatusCode.INVALID_INPUT_VALUE);
        }
        if (fieldConfig.getDimensions() != null) {
            throw new GlobalExceptionHandler(
                "Dimensions are not allowed for " + fieldConfig.getType() + " field type",
                StatusCode.INVALID_INPUT_VALUE);
        }
        if (fieldConfig.getSimilarity() != null) {
            throw new GlobalExceptionHandler(
                "Similarity is not allowed for " + fieldConfig.getType() + " field type",
                StatusCode.INVALID_INPUT_VALUE);
        }
        if (fieldConfig.getObjectMapping() != null) {
            throw new GlobalExceptionHandler(
                "Object mapping is not allowed for " + fieldConfig.getType() + " field type",
                StatusCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateKeywordType(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        validateSimpleType(fieldConfig);
    }

    private void validateLongType(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        validateSimpleType(fieldConfig);
    }

    private void validateDoubleType(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        validateSimpleType(fieldConfig);
    }

    private void validateBooleanType(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        validateSimpleType(fieldConfig);
    }

    private void validateDatetimeType(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        validateSimpleType(fieldConfig);
    }

    private void validateObjectType(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        Map<String, FieldConfig> objectMapping = fieldConfig.getObjectMapping();

        // Object mapping can be null to store as a blob
        if (objectMapping != null) {
            validateMappings(objectMapping);
        }
    }

    public void validateMappings(Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler {
        for (Map.Entry<String, FieldConfig> entry : mappings.entrySet()) {
            String fieldName = entry.getKey();
            FieldConfig fieldConfig = entry.getValue();

            if (RESERVED_FIELD_NAMES.contains(fieldName)) {
                throw new GlobalExceptionHandler(
                    RESERVED_FIELD_NAMES + " are reserved field names. your field name: "
                        + fieldName,
                    StatusCode.INVALID_INPUT_VALUE);
            }

            String fieldType = fieldConfig.getType();
            if (fieldType == null) {
                throw new GlobalExceptionHandler(
                    "type is required field." + "add type to " + fieldName,
                    StatusCode.INVALID_INPUT_VALUE);
            }

            if (supportedFieldTypes.get(fieldType) == null) {
                throw new GlobalExceptionHandler(
                    fieldType + " is not a supported field type" + "supported field types are "
                        + supportedFieldTypes.keySet(),
                    StatusCode.INVALID_INPUT_VALUE);
            }

            if (fieldName.contains(NESTED_FIELD_DELIMITER)) {
                throw new GlobalExceptionHandler(
                    "Field name cannot contain nested field delimiter: " + fieldName,
                    StatusCode.INVALID_INPUT_VALUE);
            }

            switch (fieldType) {
                case DATA_TYPE_TEXT -> validateTextType(fieldConfig);
                case DATA_TYPE_KEYWORD -> validateKeywordType(fieldConfig);
                case DATA_TYPE_LONG -> validateLongType(fieldConfig);
                case DATA_TYPE_DOUBLE -> validateDoubleType(fieldConfig);
                case DATA_TYPE_BOOLEAN -> validateBooleanType(fieldConfig);
                case DATA_TYPE_VECTOR -> validateVectorType(fieldConfig);
                case DATA_TYPE_DATETIME -> validateDatetimeType(fieldConfig);
                case DATA_TYPE_OBJECT -> validateObjectType(fieldConfig);
            }
        }
    }

    public void validateMappingsAndSetDefaults(Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler {
        if (mappings == null) {
            throw new GlobalExceptionHandler("Index mapping is required field",
                StatusCode.INVALID_INPUT_VALUE);
        }

        validateMappings(mappings);

        // Add mandatory ID field to mapping
        FieldConfig idFieldConfig = new FieldConfig();
        idFieldConfig.setType(DATA_TYPE_KEYWORD);
        mappings.put(DOC_FIELD_ID, idFieldConfig);
    }

    public boolean validateProjectRequestPath(String path) {
        // Validate project request path (e.g. /v1/projects/Ab23Sd4)
        String regex = "^/v\\d\\w*/projects(/[A-Za-z0-9]{7})?$";

        return Pattern.matches(regex, path);
    }

    public void validateContentType(Map<String, String> headers) throws GlobalExceptionHandler {
        String contentType =
            headers.get(CONTENT_TYPE) == null ? headers.get(CONTENT_TYPE_LOWER_CASE)
                : headers.get(CONTENT_TYPE);
        if (contentType == null || !contentType.equals(APPLICATION_JSON)) {
            throw new GlobalExceptionHandler("Invalid content type : "
                + contentType,
                StatusCode.BAD_REQUEST);
        }
    }

    public void validateAdminApiKey(String requestedApiKey) throws GlobalExceptionHandler {
        if (requestedApiKey == null || !requestedApiKey.equals(
            secretsManager.getSecretValue(ADMIN_API_KEY_SECRET_NAME))) {
            throw new GlobalExceptionHandler("Failed verification of the API key",
                StatusCode.UNAUTHENTICATED);
        }
    }

    public void validateProjectApiKey(String requestedApiKey, String storedApiKey)
        throws GlobalExceptionHandler {
        if (requestedApiKey == null || !requestedApiKey.equals(storedApiKey)) {
            throw new GlobalExceptionHandler("Failed verification of the API key",
                StatusCode.UNAUTHENTICATED);
        }
    }

    public void validateProjectCreate(ProjectCreateRequest request) throws GlobalExceptionHandler {
        String projectName = request.getProjectName();
        Double rateLimit = request.getRateLimit();

        if (projectName == null) {
            throw new GlobalExceptionHandler("projectName is required field",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (projectName.isEmpty()) {
            throw new GlobalExceptionHandler("projectName cannot be empty string",
                StatusCode.INVALID_INPUT_VALUE);
        }

        if (!projectName.matches("[a-zA-Z0-9_-]{3,45}")) {
            throw new GlobalExceptionHandler(projectName
                + " is not a valid project name", StatusCode.INVALID_INPUT_VALUE);
        }

        if (rateLimit == null) {
            throw new GlobalExceptionHandler("rateLimit is required field",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (rateLimit <= 0) {
            throw new GlobalExceptionHandler("rateLimit must be greater than 0.0",
                StatusCode.INVALID_INPUT_VALUE);
        }
    }

    public void validateProjectUpdate(ProjectUpdateRequest request) throws GlobalExceptionHandler {
        Double rateLimit = request.getRateLimit();

        if (rateLimit == null) {
            throw new GlobalExceptionHandler("rateLimit is required field",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (rateLimit <= 0) {
            throw new GlobalExceptionHandler("rateLimit must be greater than 0.0",
                StatusCode.INVALID_INPUT_VALUE);
        }
    }

    public void validateIndexCreate(IndexCreateRequest request) throws GlobalExceptionHandler {
        String indexName = request.getIndexName();
        String indexClass = request.getIndexClass();

        if (indexName == null) {
            throw new GlobalExceptionHandler("IndexName is required field",
                StatusCode.INVALID_INPUT_VALUE);
        } else if (indexName.isEmpty()) {
            throw new GlobalExceptionHandler("IndexName cannot be empty string",
                StatusCode.INVALID_INPUT_VALUE);
        }

        if (!indexName.matches("[a-zA-Z0-9_-]{3,52}")) {
            throw new GlobalExceptionHandler(indexName + " is not a valid index name",
                StatusCode.INVALID_INPUT_VALUE);
        }

        if (indexClass == null) {
            request.setIndexClass(INDEX_CLASS_DEFAULT);
        } else if (indexClass.isEmpty()) {
            throw new GlobalExceptionHandler("Index class cannot be empty string",
                StatusCode.INVALID_INPUT_VALUE);
        } else {
            indexClass = indexClass.toUpperCase();
            request.setIndexClass(indexClass);
            if (!indexClass.equals(INDEX_CLASS_STD)
                && !indexClass.equals(INDEX_CLASS_IA)) {
                throw new GlobalExceptionHandler("Invalid index class: " + indexClass,
                    StatusCode.INVALID_INPUT_VALUE);
            }
        }
    }

    private void _validateDocument(Map<String, JsonElement> document,
        Map<String, FieldConfig> mappings) throws GlobalExceptionHandler {
        for (Map.Entry<String, JsonElement> field : document.entrySet()) {
            String fieldName = field.getKey();
            JsonElement fieldValue = field.getValue();

            if (mappings.containsKey(fieldName)) {
                if (fieldValue == null || fieldValue.isJsonNull()) {
                    throw new GlobalExceptionHandler(
                        "Field '" + fieldName + "' must not be null or empty.",
                        StatusCode.INVALID_INPUT_VALUE);
                }

                if (RESERVED_FIELD_NAMES_FOR_VALIDATE.contains(fieldName)) {
                    throw new GlobalExceptionHandler(
                        RESERVED_FIELD_NAMES + " are reserved field names. your field name: "
                            + fieldName,
                        StatusCode.INVALID_INPUT_VALUE);
                }

                // Mapping found
                FieldConfig fieldConfig = mappings.get(fieldName);
                try {
                    switch (fieldConfig.getType()) {
                        case DATA_TYPE_TEXT -> fieldValue.getAsString();
                        case DATA_TYPE_KEYWORD -> {
                            if (!fieldValue.isJsonPrimitive() && !fieldValue.isJsonArray()) {
                                throw new GlobalExceptionHandler(
                                    "Invalid keyword value: " + fieldValue,
                                    StatusCode.INVALID_INPUT_VALUE);
                            }
                        }
                        case DATA_TYPE_LONG -> {
                            if (fieldValue.isJsonPrimitive()
                                && ((JsonPrimitive) fieldValue).isNumber()) {
                                String jsonValue = fieldValue.getAsString();

                                if (jsonValue.contains(".")) {
                                    throw new GlobalExceptionHandler(
                                        "Field '" + fieldName
                                            + "' must be an integer without any decimal part. Found: "
                                            + jsonValue,
                                        StatusCode.INVALID_INPUT_VALUE);
                                }
                            } else {
                                throw new GlobalExceptionHandler(
                                    "Field '" + fieldName + "' must be a long. Found: "
                                        + fieldValue,
                                    StatusCode.INVALID_INPUT_VALUE);
                            }
                        }
                        case DATA_TYPE_DOUBLE -> fieldValue.getAsDouble();
                        case DATA_TYPE_BOOLEAN -> {
                            String value = fieldValue.getAsString();
                            if (!BOOLEAN_VALUES.contains(value.toLowerCase())) {
                                throw new GlobalExceptionHandler(
                                    "Invalid boolean value: " + value,
                                    StatusCode.INVALID_INPUT_VALUE);
                            }
                        }
                        case DATA_TYPE_VECTOR -> {
                            float[] vector = gson.fromJson(fieldValue, float[].class);
                            if (vector.length != fieldConfig.getDimensions()) {
                                throw new GlobalExceptionHandler(
                                    "Vector dimensions do not match. your vector dimensions: "
                                        + vector.length + " expected dimensions: "
                                        + fieldConfig.getDimensions(),
                                    StatusCode.INVALID_INPUT_VALUE);
                            }
                        }
                        case DATA_TYPE_DATETIME -> {
                            OffsetDateTime ignored = stringToOffsetDateTime(
                                fieldValue.getAsString());
                        }
                        case DATA_TYPE_OBJECT -> {
                            Map<String, JsonElement> nestedDoc = gson.fromJson(
                                fieldValue, gsonMapType);
                            if (nestedDoc == null) {
                                throw new GlobalExceptionHandler(
                                    "Nested document cannot be null",
                                    StatusCode.INVALID_INPUT_VALUE);
                            } else if (fieldConfig.getObjectMapping() != null) {
                                GlobalLogger.debug(
                                    "Validating nested document: " + fieldName + " -> "
                                        + nestedDoc);
                                _validateDocument(nestedDoc,
                                    fieldConfig.getObjectMapping());
                            }
                        }
                    }
                } catch (UnsupportedOperationException | IllegalStateException |
                         NumberFormatException | DateTimeParseException e) {
                    GlobalLogger.debug("Failed to validate document field: " + fieldName);
                    GlobalLogger.debug(e.getMessage());
                    throw new GlobalExceptionHandler(
                        "Failed to validate document field: " + fieldName,
                        StatusCode.INVALID_INPUT_VALUE);
                }
            }
        }
    }

    public void validateDocument(Map<String, JsonElement> document,
        Map<String, FieldConfig> mappings) throws GlobalExceptionHandler {

        _validateDocument(document, mappings);
    }
}
