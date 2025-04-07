package io.wrtn.engine.lucene.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.wrtn.engine.lucene.query.SearchQueryBuilder;
import io.wrtn.engine.lucene.query.SortBuilder;
import io.wrtn.model.index.FieldConfig;
import io.wrtn.util.StatusCode;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;

import java.util.Map;
import io.wrtn.util.GlobalExceptionHandler;
import org.joda.time.IllegalInstantException;

public final class Validation {

    public static boolean validateSort(JsonArray sort, Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler{
        try {
            SortBuilder.build(sort, mappings);
        } catch (IllegalArgumentException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.INVALID_INPUT_VALUE);
        }

        return true;
    }

    public static boolean validateQuery(JsonObject jsonQuery, Map<String, FieldConfig> mappings)
        throws GlobalExceptionHandler {
        Query query;
        try {
            query = SearchQueryBuilder.build(jsonQuery, mappings);
        } catch (QueryNodeException | IllegalInstantException e) {
            throw new GlobalExceptionHandler(e.getMessage(), StatusCode.INVALID_INPUT_VALUE);
        }

        if (query == null) {
            return false;
        }

        MappingValidationQueryVisitor visitor = new MappingValidationQueryVisitor(mappings);
        query.visit(visitor);

        return visitor.isValid;
    }

    private static final class MappingValidationQueryVisitor extends QueryVisitor {

        final Map<String, FieldConfig> mappings;
        boolean isValid;

        MappingValidationQueryVisitor(Map<String, FieldConfig> mappings) {
            this.mappings = mappings;
            this.isValid = true;
        }

        @Override
        public boolean acceptField(String field) {
            if (mappings.get(field) == null) {
                isValid = false;
            }

            return true;
        }
    }
}
