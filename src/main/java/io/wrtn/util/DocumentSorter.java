package io.wrtn.util;

import static io.wrtn.engine.lucene.Constants.DATA_TYPE_DATETIME;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_DOUBLE;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_KEYWORD;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_LONG;
import static io.wrtn.engine.lucene.Constants.DOC_FIELD_ID;
import static io.wrtn.engine.lucene.Constants.NESTED_FIELD_DELIMITER_ESCAPED;
import static io.wrtn.engine.lucene.Constants.SORT_DIRECTION_DESC;
import static io.wrtn.engine.lucene.Constants.SUPPORTED_SORT_FIELDS;
import static io.wrtn.util.JsonParser.exceptionGson;
import static io.wrtn.util.TimeUtil.stringToTime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.wrtn.dto.QueryRequest;
import io.wrtn.model.document.Document;
import io.wrtn.model.document.RefreshedDocs;
import io.wrtn.model.index.FieldConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DocumentSorter {

    public static Document[] sortDocuments(List<Document[]> docs, RefreshedDocs refreshedDocs,
        Map<String, FieldConfig> mappings,
        QueryRequest request) throws GlobalExceptionHandler {

        Document[] mergedDocs;
        if (refreshedDocs != null) {
            mergedDocs = mergeWithRefreshedDocs(docs, refreshedDocs);
        } else {
            mergedDocs = docs.stream()
                .flatMap(Arrays::stream)
                .toArray(Document[]::new);
        }

        Document[] sortedDocs;
        if (request.getSort() == null) {
            Comparator<Document> scoreComparator = Comparator.comparing(Document::getScore,
                Comparator.nullsFirst(Comparator.naturalOrder())).reversed();
            Comparator<Document> defaultIdcomparator = createComparatorByType(DOC_FIELD_ID,
                DATA_TYPE_KEYWORD, false);
            Comparator<Document> combinedComparator = scoreComparator.thenComparing(
                defaultIdcomparator);

            sortedDocs = Arrays.stream(mergedDocs)
                .sorted(combinedComparator)
                .limit(request.getSize())
                .toArray(Document[]::new);

        } else {
            sortedDocs = _sortDocuments(mergedDocs, mappings, request.getSort(), request.getSize());
        }
        return sortedDocs;
    }

    public static Document[] _sortDocuments(Document[] documents, Map<String, FieldConfig> mappings,
        JsonArray sortArray, int size) throws GlobalExceptionHandler {

        if (documents == null || documents.length <= 1 || sortArray == null
            || sortArray.isEmpty()) {
            return documents;
        }

        List<Comparator<Document>> comparators = new ArrayList<>();

        for (JsonElement sortElement : sortArray) {

            JsonObject sortObject = sortElement.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : sortObject.entrySet()) {
                String fieldName = entry.getKey();
                JsonElement directionElement = entry.getValue();

                String direction = directionElement.getAsString().toLowerCase();
                String fieldType = getValidatedFieldType(fieldName, mappings);

                if (SUPPORTED_SORT_FIELDS.contains(fieldType)) {
                    Comparator<Document> comparator = createComparatorByType(fieldName, fieldType,
                        direction);
                    comparators.add(comparator);
                }
            }
            Comparator<Document> defaultIdcomparator = createComparatorByType(DOC_FIELD_ID,
                DATA_TYPE_KEYWORD, false);
            comparators.add(defaultIdcomparator);
        }

        Comparator<Document> combinedComparator = null;
        for (Comparator<Document> comparator : comparators) {
            if (combinedComparator == null) {
                combinedComparator = comparator;
            } else {
                combinedComparator = combinedComparator.thenComparing(comparator);
            }
        }

        if (combinedComparator != null) {
            return Arrays.stream(documents)
                .sorted(combinedComparator)
                .limit(size)
                .toArray(Document[]::new);
        }

        return documents;
    }

    private static Comparator<Document> createComparatorByType(String fieldName, String fieldType,
        String direction) throws GlobalExceptionHandler {
        return createComparatorByType(fieldName, fieldType, direction.equals(SORT_DIRECTION_DESC));
    }

    private static Comparator<Document> createComparatorByType(String fieldName, String fieldType,
        boolean isDescending) throws GlobalExceptionHandler {

        Comparator<Document> comparator;
        switch (fieldType.toLowerCase()) {
            case DATA_TYPE_DATETIME:
                comparator = (doc1, doc2) -> {
                    Long val1 = getDateTimeValue(doc1, fieldName);
                    Long val2 = getDateTimeValue(doc2, fieldName);
                    return compareValues(val1, val2, isDescending);
                };
                break;

            case DATA_TYPE_LONG:
                comparator = (doc1, doc2) -> {
                    Long val1 = getLongValue(doc1, fieldName);
                    Long val2 = getLongValue(doc2, fieldName);
                    return compareValues(val1, val2, isDescending);
                };
                break;

            case DATA_TYPE_DOUBLE:
                comparator = (doc1, doc2) -> {
                    Double val1 = getDoubleValue(doc1, fieldName);
                    Double val2 = getDoubleValue(doc2, fieldName);
                    return compareValues(val1, val2, isDescending);
                };
                break;

            case DATA_TYPE_KEYWORD:
                comparator = (doc1, doc2) -> {
                    String val1 = getStringValue(doc1, fieldName);
                    String val2 = getStringValue(doc2, fieldName);
                    return compareValues(val1, val2, isDescending);
                };
                break;

            default:
                throw new GlobalExceptionHandler(
                    "Unsupported sort field type: " + fieldType,
                    StatusCode.INVALID_INPUT_VALUE);
        }

        return comparator;
    }

    private static Long getDateTimeValue(Document doc, String fieldName) {
        JsonElement element = getFieldValue(doc, fieldName);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return stringToTime(element.getAsString());
        } catch (GlobalExceptionHandler ge) {
            throw new RuntimeException(
                exceptionGson.toJson(ge, GlobalExceptionHandler.class));
        }
    }

    private static Long getLongValue(Document doc, String fieldName) {
        JsonElement element = getFieldValue(doc, fieldName);
        return (element != null && !element.isJsonNull()) ? element.getAsLong() : null;
    }

    private static Double getDoubleValue(Document doc, String fieldName) {
        JsonElement element = getFieldValue(doc, fieldName);
        return (element != null && !element.isJsonNull()) ? element.getAsDouble() : null;
    }

    private static String getStringValue(Document doc, String fieldName) {
        JsonElement element = getFieldValue(doc, fieldName);

        if (element == null || element.isJsonNull() || element.isJsonArray()) {
            return null;
        }

        return element.getAsString();
    }

    private static <T extends Comparable<T>> int compareValues(T val1, T val2,
        boolean isDescending) {

        if (val1 == null && val2 == null) {
            return 0;
        }
        if (val1 == null) {
            return 1;
        }
        if (val2 == null) {
            return -1;
        }

        int result = val1.compareTo(val2);
        return isDescending ? -result : result;
    }

    private static JsonElement getFieldValue(Document doc, String fieldName) {
        String[] fieldParts = fieldName.split(NESTED_FIELD_DELIMITER_ESCAPED);
        JsonElement current = doc.getDoc();

        for (String part : fieldParts) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }

            JsonObject currentObj = current.getAsJsonObject();
            if (!currentObj.has(part)) {
                return null;
            }

            current = currentObj.get(part);
        }

        return current;
    }

    public static Document[] mergeWithRefreshedDocs(List<Document[]> docs,
        RefreshedDocs refreshedDocs) {
        // Merge documents with deleted documents filtering
        Map<String, Document> docMap = new HashMap<>();
        for (Document[] documents : docs) {
            for (Document document : documents) {
                String docId = document.getDoc().get(DOC_FIELD_ID).getAsString();
                if (!refreshedDocs.getDeletedDocIdSet().contains(docId)) {
                    docMap.put(docId, document);
                }
            }
        }

        // Overwrite with refreshed (latest) documents
        for (Document document : refreshedDocs.getDocuments()) {
            docMap.put(document.getDoc().get(DOC_FIELD_ID).getAsString(), document);
        }

        return docMap.values().toArray(Document[]::new);
    }

    public static Document[] mergeDocs(List<Document[]> docs) {
        return docs.stream()
            .flatMap(Arrays::stream)
            .toArray(Document[]::new);
    }

    private static String getValidatedFieldType(String fieldName,
        Map<String, FieldConfig> mappings) {

        String[] fieldParts = fieldName.split(NESTED_FIELD_DELIMITER_ESCAPED);
        FieldConfig currentConfig;
        Map<String, FieldConfig> currentMappings = mappings;

        for (int i = 0; i < fieldParts.length - 1; i++) {
            String part = fieldParts[i];
            currentConfig = currentMappings.get(part);
            currentMappings = currentConfig.getObjectMapping();

        }

        String lastPart = fieldParts[fieldParts.length - 1];
        return currentMappings.get(lastPart).getType();
    }
}
