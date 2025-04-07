package io.wrtn.engine.lucene.query;

import com.google.gson.JsonObject;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.Query;

import java.util.Map;

import static io.wrtn.engine.lucene.Constants.*;
import static io.wrtn.util.JsonParser.gson;

public final class KnnFloatVectorQueryBuilder {

    public static Query build(final JsonObject vectorObject,
        final Map<String, FieldConfig> mappings)
        throws QueryNodeException, GlobalExceptionHandler {

        // get query parameters
        String field = getField(vectorObject);
        float[] vectors = getVectors(vectorObject);
        Integer topK = getTopK(vectorObject);
        Integer numCandidates = getNumCandidates(vectorObject);
        validateQueryParams(field, vectors, topK, numCandidates, mappings);

        Query filterQuery = getFilterQuery(vectorObject, mappings);
        Query knnVectorQuery = numCandidates == null ?
            new FilteredKnnFloatVectorQuery(field, vectors, topK, filterQuery) :
            new FilteredKnnFloatVectorQuery(field, vectors, topK, numCandidates, filterQuery);

        return BoostQueryBuilder.build(knnVectorQuery, vectorObject);
    }

    private static String getField(JsonObject vectorObject) {
        return vectorObject.get(QUERY_PARAM_QUERY_VECTOR_FIELD).getAsString();
    }

    private static float[] getVectors(JsonObject vectorObject) {
        return gson.fromJson(vectorObject.get(QUERY_PARAM_VECTOR), float[].class);
    }

    private static Integer getTopK(JsonObject vectorObject) {
        return vectorObject.get(QUERY_PARAM_TOP_K).getAsInt();
    }

    // If numCandidates is not provided, it will be set to topK
    private static Integer getNumCandidates(JsonObject vectorObject) {
        return vectorObject.has(QUERY_PARAM_NUM_CANDIDATES) ? vectorObject.get(
            QUERY_PARAM_NUM_CANDIDATES).getAsInt() : null;
    }

    private static void validateQueryParams(String field, float[] vectors, Integer topK,
        Integer numCandidates, Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler {

        Integer dimensionInMappings = mappings.get(field).getDimensions();

        if (vectors == null || vectors.length != dimensionInMappings) {
            throw new GlobalExceptionHandler(
                "queryVector size must be equal to dimension of field " + field + ". Expected: "
                    + dimensionInMappings, StatusCode.INVALID_INPUT_VALUE);
        }

        if (topK <= 0) {
            throw new GlobalExceptionHandler("topK must be greater than 0",
                StatusCode.INVALID_INPUT_VALUE);
        }

        if (numCandidates != null) {
            if (numCandidates > NUM_CANDIDATES_LIMIT) {
                throw new GlobalExceptionHandler(
                    "numCandidates must be less than or equal to " + NUM_CANDIDATES_LIMIT,
                    StatusCode.INVALID_INPUT_VALUE);
            }
            if (numCandidates <= 0) {
                throw new GlobalExceptionHandler("numCandidates must be greater than 0",
                    StatusCode.INVALID_INPUT_VALUE);
            }
            if (numCandidates < topK) {
                throw new GlobalExceptionHandler(
                    "numCandidates must be greater than or equal to topK",
                    StatusCode.INVALID_INPUT_VALUE);
            }
        }
    }

    private static Query getFilterQuery(JsonObject vectorObject, Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler, QueryNodeException {

        if (vectorObject.has(QUERY_PARAM_FILTER)) {
            JsonObject filter = vectorObject.getAsJsonObject(QUERY_PARAM_FILTER);
            JsonObject queryString = filter.getAsJsonObject(QUERY_TYPE_QUERY_STRING);
            JsonObject[] boolArray = gson.fromJson(filter.get(QUERY_TYPE_BOOL), JsonObject[].class);

            if (queryString == null && boolArray == null) {
                throw new GlobalExceptionHandler(
                    "filter in knn query must contain queryString or bool",
                    StatusCode.INVALID_INPUT_VALUE);
            }

            return queryString != null ? QueryStringQueryBuilder.build(queryString, mappings) :
                BooleanQueryBuilder.build(boolArray, mappings);
        } else {
            return null;
        }
    }
}