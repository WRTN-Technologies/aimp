package io.wrtn.engine.lucene.query;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.util.GlobalExceptionHandler;
import io.wrtn.util.StatusCode;
import java.util.Arrays;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.Query;

import java.lang.reflect.Type;
import java.util.Map;

import static io.wrtn.engine.lucene.Constants.*;
import static io.wrtn.util.JsonParser.gson;

public final class SearchQueryBuilder {

    private static final Type JSON_OBJECT_ARRAY_TYPE = TypeToken.getArray(JsonObject.class)
        .getType();

    private interface QueryBuilder {

        Query apply(JsonObject jsonQuery, Map<String, FieldConfig> mappings)
            throws QueryNodeException, GlobalExceptionHandler;
    }

    private enum QueryType {
        BOOL(QUERY_TYPE_BOOL, SearchQueryBuilder::buildBooleanQuery),
        QUERY_STRING(QUERY_TYPE_QUERY_STRING, SearchQueryBuilder::buildQueryStringQuery),
        KNN(QUERY_TYPE_KNN, SearchQueryBuilder::buildKnnQuery);

        private final String key;
        private final QueryBuilder builder;

        QueryType(String key, QueryBuilder builder) {
            this.key = key;
            this.builder = builder;
        }

        Query build(JsonObject jsonQuery, Map<String, FieldConfig> mappings)
            throws IllegalArgumentException, QueryNodeException, GlobalExceptionHandler {

            return builder.apply(jsonQuery, mappings);
        }
    }

    public static Query build(final JsonObject jsonQuery, final Map<String, FieldConfig> mappings)
        throws IllegalArgumentException, QueryNodeException, GlobalExceptionHandler {

        if (jsonQuery == null) {
            throw new GlobalExceptionHandler("Query cannot be null.", StatusCode.BAD_REQUEST);
        }
        assert mappings != null : "mappings cannot be null.";

        try {
            return Arrays.stream(QueryType.values())
                .filter(type -> jsonQuery.has(type.key))
                .findFirst()
                .map(type -> {
                    try {
                        return type.build(jsonQuery, mappings);
                    } catch (QueryNodeException | GlobalExceptionHandler e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElseThrow(() -> new RuntimeException(new GlobalExceptionHandler(
                    "One of booleanQuery, queryStringQuery, or knnFloatVectorQuery must be included in the query syntax.",
                    StatusCode.BAD_REQUEST)));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof GlobalExceptionHandler) {
                throw (GlobalExceptionHandler) e.getCause();
            }
            throw e;
        }
    }

    private static Query buildBooleanQuery(JsonObject jsonQuery, Map<String, FieldConfig> mappings)
        throws QueryNodeException, GlobalExceptionHandler {
        JsonObject[] boolArray;
        try {
            boolArray = gson.fromJson(jsonQuery.getAsJsonArray(QUERY_TYPE_BOOL),
                JSON_OBJECT_ARRAY_TYPE);
        } catch (ClassCastException | JsonSyntaxException e) {
            throw new GlobalExceptionHandler("bool must be a valid array.", StatusCode.BAD_REQUEST);
        }

        if (boolArray == null) {
            throw new GlobalExceptionHandler("bool is a required field.", StatusCode.BAD_REQUEST);
        }
        return BooleanQueryBuilder.build(boolArray, mappings);
    }

    private static Query buildQueryStringQuery(JsonObject jsonQuery,
        Map<String, FieldConfig> mappings)
        throws QueryNodeException, GlobalExceptionHandler {
        JsonObject queryString;
        try {
            queryString = jsonQuery.getAsJsonObject(QUERY_TYPE_QUERY_STRING);
        } catch (ClassCastException e) {
            throw new GlobalExceptionHandler("query must be an object.",
                StatusCode.BAD_REQUEST);
        }

        if (queryString == null || !queryString.has(QUERY_TYPE_QUERY)) {
            throw new GlobalExceptionHandler("query is a required field in queryStringQuery",
                StatusCode.BAD_REQUEST);
        }
        return QueryStringQueryBuilder.build(queryString, mappings);
    }

    private static Query buildKnnQuery(JsonObject jsonQuery, Map<String, FieldConfig> mappings)
        throws QueryNodeException, GlobalExceptionHandler {
        JsonObject knnQuery = jsonQuery.getAsJsonObject(QUERY_TYPE_KNN);

        if (knnQuery == null) {
            throw new GlobalExceptionHandler("vectorQuery is a required field.",
                StatusCode.BAD_REQUEST);
        }
        return KnnFloatVectorQueryBuilder.build(knnQuery, mappings);
    }
}