package io.wrtn.engine.lucene.query;

import com.google.gson.JsonObject;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.util.StatusCode;
import java.util.stream.Stream;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher.TooManyClauses;
import org.apache.lucene.search.Query;

import java.util.Map;
import io.wrtn.util.GlobalExceptionHandler;

import static io.wrtn.engine.lucene.Constants.DEFAULT_MAX_CLAUSE_COUNT;
import static io.wrtn.engine.lucene.Constants.QUERY_ENUM_SHOULD;
import static io.wrtn.engine.lucene.Constants.QUERY_PARAM_OCCUR;
import static io.wrtn.engine.lucene.Constants.QUERY_TYPE_BOOL;
import static io.wrtn.engine.lucene.Constants.QUERY_TYPE_KNN;
import static io.wrtn.engine.lucene.Constants.QUERY_TYPE_QUERY_STRING;
import static io.wrtn.util.JsonParser.gson;

public final class BooleanQueryBuilder {

    @FunctionalInterface
    private interface QueryBuilder {

        Query build(JsonObject boolObject, Map<String, FieldConfig> mappings)
            throws QueryNodeException, GlobalExceptionHandler;
    }

    private enum SubQueryType {
        BOOL(QUERY_TYPE_BOOL, (boolObject, mappings) -> BooleanQueryBuilder.build(
            gson.fromJson(boolObject.get(QUERY_TYPE_BOOL), JsonObject[].class), mappings)),
        KNN(QUERY_TYPE_KNN,
            (boolObject, mappings) -> KnnFloatVectorQueryBuilder.build(
                boolObject.getAsJsonObject(QUERY_TYPE_KNN),
                mappings)),
        QUERY_STRING(QUERY_TYPE_QUERY_STRING,
            (boolObject, mappings) -> QueryStringQueryBuilder.build(
                boolObject.getAsJsonObject(QUERY_TYPE_QUERY_STRING),
                mappings));

        private final String key;
        private final QueryBuilder builder;

        SubQueryType(String key, QueryBuilder builder) {
            this.key = key;
            this.builder = builder;
        }

        public static SubQueryType fromJsonObject(JsonObject boolObject)
            throws GlobalExceptionHandler {
            return Stream.of(values())
                .filter(type -> boolObject.has(type.key))
                .findFirst()
                .orElseThrow(
                    () -> new GlobalExceptionHandler("Unknown query type", StatusCode.BAD_REQUEST));
        }

        public Query build(JsonObject boolObject, Map<String, FieldConfig> mappings)
            throws QueryNodeException, GlobalExceptionHandler {
            return builder.build(boolObject, mappings);
        }
    }

    public static Query build(final JsonObject[] boolArray, final Map<String, FieldConfig> mappings)
        throws QueryNodeException, GlobalExceptionHandler {
        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();

        for (JsonObject boolObject : boolArray) {
            BooleanClause.Occur occur = getOccur(boolObject);
            Query query = buildSubQuery(boolObject, mappings);
            query = BoostQueryBuilder.build(query, boolObject);
            try {
                booleanQueryBuilder.add(query, occur);
            } catch (TooManyClauses e) {
                throw new GlobalExceptionHandler(
                    "Too many boolean clauses, the maximum is " + DEFAULT_MAX_CLAUSE_COUNT,
                    StatusCode.BAD_REQUEST);
            }
        }

        return booleanQueryBuilder.build();
    }

    private static BooleanClause.Occur getOccur(JsonObject boolObject) {
        String occurString = boolObject.has(QUERY_PARAM_OCCUR) ?
            boolObject.get(QUERY_PARAM_OCCUR).getAsString().toUpperCase() : QUERY_ENUM_SHOULD;
        return BooleanClause.Occur.valueOf(occurString);
    }

    private static Query buildSubQuery(JsonObject boolObject, Map<String, FieldConfig> mappings)
        throws QueryNodeException, GlobalExceptionHandler {
        SubQueryType queryType = SubQueryType.fromJsonObject(boolObject);
        return queryType.build(boolObject, mappings);
    }
}