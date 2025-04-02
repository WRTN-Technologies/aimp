package io.wrtn.engine.lucene.util;

import static io.wrtn.engine.lucene.Constants.DATA_TYPE_BOOLEAN;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_DATETIME;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_DOUBLE;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_KEYWORD;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_LONG;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_OBJECT;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_TEXT;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_VECTOR;
import static io.wrtn.engine.lucene.Constants.DEFAULT_IGNORE_ABOVE;
import static io.wrtn.engine.lucene.Constants.NESTED_FIELD_DELIMITER;
import static io.wrtn.engine.lucene.Constants.SORT_FIELD_SUFFIX;
import static io.wrtn.engine.lucene.Constants.VECTOR_SIMILARITY_ARRAY;
import static io.wrtn.engine.lucene.Constants.VECTOR_SIMILARITY_DEFAULT;
import static io.wrtn.engine.lucene.Constants.VECTOR_SIMILARITY_L2_NORM;
import static io.wrtn.engine.lucene.Constants.VECTOR_SIMILARITY_L2_NORM_INTERNAL;
import static io.wrtn.engine.lucene.Constants.VECTOR_VALUE_SUFFIX;
import static io.wrtn.util.JsonParser.gson;
import static io.wrtn.util.TimeUtil.stringToTime;

import com.google.gson.JsonElement;
import io.wrtn.engine.lucene.QueryResultBuilder;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.GlobalLogger;
import io.wrtn.util.StatusCode;
import io.wrtn.util.Validation;
import io.wrtn.util.ValueConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;

public class DocUtils {

    public static List<io.wrtn.model.document.Document> fetchDocuments(TopDocs topDocs,
        boolean includeVectors, boolean includeScores, String[] fields, IndexSearcher searcher,
        String indexName) throws IOException {
        List<io.wrtn.model.document.Document> docs = new ArrayList<>();
        for (ScoreDoc hit : topDocs.scoreDocs) {
            io.wrtn.model.document.Document doc = new io.wrtn.model.document.Document();
            doc.setIndex(indexName);
            if (includeScores) {
                doc.setScore(hit.score);
            }
            doc.setDoc(QueryResultBuilder.buildDocument(searcher.storedFields()
                .document(hit.doc), includeVectors, fields));

            docs.add(doc);
        }

        return docs;
    }

    public static void buildLuceneDocument(Map<String, JsonElement> doc, Document luceneDoc,
        String baseName, Map<String, FieldConfig> mappings) throws GlobalExceptionHandler {
        List<String> nestedFieldNames = new ArrayList<>();
        List<String> vectorFieldNames = new ArrayList<>();
        List<Map<String, JsonElement>> nestedDocs = new ArrayList<>();
        for (Entry<String, JsonElement> entry : doc.entrySet()) {
            String fieldName = entry.getKey();
            JsonElement fieldValue = entry.getValue();
            FieldConfig fieldConfig = mappings.get(fieldName);
            if (fieldValue.isJsonNull() || fieldConfig == null) {
                continue;
            }

            if (baseName != null) {
                fieldName = baseName + NESTED_FIELD_DELIMITER + fieldName;
            }
            switch (fieldConfig.getType()) {
                case DATA_TYPE_VECTOR -> {
                    float[] values = gson.fromJson(fieldValue, float[].class);
                    luceneDoc.add(
                        new KnnFloatVectorField(fieldName, values,
                            VectorSimilarityFunction.valueOf(getSimilarity(fieldConfig))));
                    luceneDoc.add(
                        new StoredField(fieldName + NESTED_FIELD_DELIMITER + VECTOR_VALUE_SUFFIX,
                            ValueConverter.floatArrayToByteArray(values)));
                    vectorFieldNames.add(entry.getKey());
                }
                case DATA_TYPE_TEXT -> {
                    for (String analyzerName : fieldConfig.getAnalyzers()) {
                        luceneDoc.add(
                            new TextField(fieldName + NESTED_FIELD_DELIMITER + analyzerName,
                                fieldValue.getAsString(), Store.NO));
                    }
                }
                case DATA_TYPE_KEYWORD -> {
                    if (fieldValue.isJsonPrimitive()) {
                        String keyword = fieldValue.getAsString();
                        if (keyword.length() <= DEFAULT_IGNORE_ABOVE) {
                            luceneDoc.add(new StringField(fieldName, keyword, Store.NO));
                            luceneDoc.add(
                                new SortedDocValuesField(
                                    fieldName + NESTED_FIELD_DELIMITER + SORT_FIELD_SUFFIX,
                                    new BytesRef(fieldValue.getAsString())));
                        }
                    } else if (fieldValue.isJsonArray()) {
                        for (JsonElement element : fieldValue.getAsJsonArray()) {
                            String keyword = element.getAsString();
                            if (keyword.length() <= DEFAULT_IGNORE_ABOVE) {
                                luceneDoc.add(new StringField(fieldName, keyword, Store.NO));
                            }
                        }
                    } else {
                        throw new GlobalExceptionHandler(
                            "Keyword field must be a string or an array of strings",
                            StatusCode.INVALID_INPUT_VALUE);
                    }
                }
                case DATA_TYPE_BOOLEAN ->
                    luceneDoc.add(new StringField(fieldName, fieldValue.getAsString(), Store.NO));
                case DATA_TYPE_LONG -> {
                    luceneDoc.add(new LongField(fieldName, fieldValue.getAsLong(), Store.NO));
                    luceneDoc.add(
                        new NumericDocValuesField(
                            fieldName + NESTED_FIELD_DELIMITER + SORT_FIELD_SUFFIX,
                            fieldValue.getAsLong()));
                }
                case DATA_TYPE_DOUBLE -> {
                    luceneDoc.add(new DoubleField(fieldName, fieldValue.getAsDouble(), Store.NO));
                    luceneDoc.add(
                        new DoubleDocValuesField(
                            fieldName + NESTED_FIELD_DELIMITER + SORT_FIELD_SUFFIX,
                            fieldValue.getAsDouble()));
                }
                case DATA_TYPE_DATETIME -> {
                    luceneDoc.add(
                        new LongField(fieldName, stringToTime(fieldValue.getAsString()), Store.NO));
                    luceneDoc.add(
                        new NumericDocValuesField(
                            fieldName + NESTED_FIELD_DELIMITER + SORT_FIELD_SUFFIX,
                            stringToTime(fieldValue.getAsString())));
                }
                case DATA_TYPE_OBJECT -> {
                    if (fieldConfig.getObjectMapping() != null) {
                        Map<String, JsonElement> nestedDoc = gson.fromJson(fieldValue,
                            Validation.gsonMapType);
                        Map<String, FieldConfig> nestedMappings = fieldConfig.getObjectMapping();
                        buildLuceneDocument(nestedDoc, luceneDoc, fieldName, nestedMappings);
                        nestedDocs.add(nestedDoc);
                        nestedFieldNames.add(entry.getKey());
                    }
                }
                default -> {
                    GlobalLogger.error("type " + fieldConfig.getType() + " is not supported");
                    throw new GlobalExceptionHandler("unsupported type " + fieldConfig.getType(),
                        StatusCode.INVALID_INPUT_VALUE);
                }
            }
        }

        for (String vectorFieldName : vectorFieldNames) {
            doc.remove(vectorFieldName);
        }

        for (String nestedFieldName : nestedFieldNames) {
            Map<String, JsonElement> nestedDoc =
                nestedDocs.get(nestedFieldNames.indexOf(nestedFieldName));

            doc.put(nestedFieldName, gson.toJsonTree(nestedDoc));
        }
    }

    private static String getSimilarity(FieldConfig fieldConfig) throws GlobalExceptionHandler {
        String similarity = VECTOR_SIMILARITY_DEFAULT;

        if (fieldConfig.getSimilarity() != null) {
            similarity = fieldConfig.getSimilarity();
            if (!VECTOR_SIMILARITY_ARRAY.contains(similarity)) {
                throw new GlobalExceptionHandler(
                    "VectorSimilarityFunction must be in " + VECTOR_SIMILARITY_ARRAY
                        + "but your value is " + similarity, StatusCode.INVALID_INPUT_VALUE);
            }

            if (similarity.equals(VECTOR_SIMILARITY_L2_NORM)) {
                similarity = VECTOR_SIMILARITY_L2_NORM_INTERNAL;
            }
        }

        return similarity.toUpperCase();
    }
}
