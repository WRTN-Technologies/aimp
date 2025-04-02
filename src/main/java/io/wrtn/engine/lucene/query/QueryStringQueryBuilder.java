package io.wrtn.engine.lucene.query;

import com.google.gson.JsonObject;

import io.wrtn.util.GlobalLogger;
import io.wrtn.util.StatusCode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.wrtn.model.index.FieldConfig;
import java.util.Objects;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.OrQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.core.parser.EscapeQuerySyntax;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.queryparser.flexible.standard.nodes.TermRangeQueryNode;
import org.apache.lucene.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.apache.lucene.search.IndexSearcher.TooManyClauses;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import io.wrtn.engine.lucene.analysis.AnalyzerBuilder;
import io.wrtn.util.GlobalExceptionHandler;

import static io.wrtn.engine.lucene.Constants.DATA_TYPE_DATETIME;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_DOUBLE;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_LONG;
import static io.wrtn.engine.lucene.Constants.DATA_TYPE_TEXT;
import static io.wrtn.engine.lucene.Constants.DEFAULT_MAX_CLAUSE_COUNT;
import static io.wrtn.engine.lucene.Constants.NESTED_FIELD_DELIMITER;
import static io.wrtn.engine.lucene.Constants.NESTED_FIELD_DELIMITER_ESCAPED;
import static io.wrtn.engine.lucene.Constants.QUERY_PARAM_DEFAULT_FIELD;
import static io.wrtn.engine.lucene.Constants.QUERY_PARAM_SKIP_SYNTAX;
import static io.wrtn.engine.lucene.Constants.QUERY_SYNTAX_MATCH_ALL;
import static io.wrtn.engine.lucene.Constants.QUERY_SYNTAX_WILDCARD;
import static io.wrtn.engine.lucene.Constants.QUERY_SYNTAX_FIELD_DELIMITER;
import static io.wrtn.engine.lucene.Constants.QUERY_SYNTAX_RANGE_START_EXCLUSIVE;
import static io.wrtn.engine.lucene.Constants.QUERY_SYNTAX_RANGE_START_INCLUSIVE;
import static io.wrtn.engine.lucene.Constants.QUERY_TYPE_QUERY;
import static io.wrtn.engine.lucene.Constants.QUERY_SYNTAX_RANGE_DELIMITER;
import static io.wrtn.engine.lucene.Constants.QUERY_SYNTAX_RANGE_END_EXCLUSIVE;
import static io.wrtn.engine.lucene.Constants.QUERY_SYNTAX_RANGE_END_INCLUSIVE;
import static io.wrtn.util.TimeUtil.stringToTime;

public final class QueryStringQueryBuilder {

    private static final StandardSyntaxParser syntaxParser = new StandardSyntaxParser();
    private static final Analyzer defaultAnalyzer = new StandardAnalyzer();
    private static final EscapeQuerySyntaxImpl escapeQuerySyntax = new EscapeQuerySyntaxImpl();
    private static final PointsConfig longPointsConfig = new PointsConfig(new DecimalFormat(),
        Long.class);
    private static final PointsConfig doublePointsConfig = new PointsConfig(new DecimalFormat(),
        Double.class);

    public static Query build(final JsonObject queryStringObject,
        final Map<String, FieldConfig> mappings) throws QueryNodeException, GlobalExceptionHandler {

        try {
            String queryString = queryStringObject.get(QUERY_TYPE_QUERY).getAsString();

            if (queryString.trim().isEmpty()) {
                throw new GlobalExceptionHandler(
                    "query string is empty or invalid. your query: " + queryString,
                    StatusCode.INVALID_INPUT_VALUE);
            }
            if (queryString.equals(QUERY_SYNTAX_MATCH_ALL)) {
                return new MatchAllDocsQuery();
            }

            String defaultField = (queryStringObject.has(QUERY_PARAM_DEFAULT_FIELD)) ?
                queryStringObject.get(QUERY_PARAM_DEFAULT_FIELD).getAsString() : null;
            Map<String, Analyzer> perFieldAnalyzers = AnalyzerBuilder.build(mappings);
            PerFieldAnalyzerWrapper perFieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(
                defaultAnalyzer, perFieldAnalyzers);

            if (queryStringObject.has(QUERY_PARAM_SKIP_SYNTAX)) {
                if (queryStringObject.get(QUERY_PARAM_SKIP_SYNTAX).getAsBoolean()) {
                    if (defaultField == null) {
                        throw new GlobalExceptionHandler(
                            "default field is required when skipSyntax is true",
                            StatusCode.INVALID_INPUT_VALUE);
                    } else {
                        return MatchQueryBuilder.build(perFieldAnalyzers, defaultField,
                            queryString);
                    }
                }
            }

            QueryNode queryTree = rewriteQueryTree(syntaxParser.parse(queryString, defaultField),
                mappings, defaultField);

            StandardQueryParser parser = new StandardQueryParser(perFieldAnalyzerWrapper);
            parser.setAllowLeadingWildcard(true);

            Map<String, PointsConfig> pointsConfigMap = new HashMap<>();
            for (Map.Entry<String, FieldConfig> entry : mappings.entrySet()) {
                FieldConfig fieldConfig = entry.getValue();
                if (fieldConfig.getType().equals(DATA_TYPE_LONG) || fieldConfig.getType()
                    .equals(DATA_TYPE_DATETIME)) {
                    pointsConfigMap.put(entry.getKey(),
                        longPointsConfig);
                } else if (fieldConfig.getType().equals(DATA_TYPE_DOUBLE)) {
                    pointsConfigMap.put(entry.getKey(),
                        doublePointsConfig);
                }
            }
            parser.setPointsConfigMap(pointsConfigMap);

            Query queryStringQuery = parser.parse(
                (String) queryTree.toQueryString(escapeQuerySyntax),
                defaultField);

            return BoostQueryBuilder.build(queryStringQuery, queryStringObject);
        } catch (QueryNodeException e) {
            if (e.getCause() instanceof TooManyClauses) {
                throw new GlobalExceptionHandler(
                    "Too many boolean clauses, the maximum is "
                        + DEFAULT_MAX_CLAUSE_COUNT,
                    StatusCode.BAD_REQUEST);
            } else {
                throw new GlobalExceptionHandler(e.getMessage(), StatusCode.BAD_REQUEST);
            }
        } catch (GlobalExceptionHandler e) {
            throw new GlobalExceptionHandler(e.getMessage(), e.getErrorCode());
        } catch (Exception e) {
            GlobalLogger.error(e.getMessage());
            throw new GlobalExceptionHandler("Invalid query string",
                StatusCode.SERVER_ERROR);
        }
    }

    private static QueryNode rewriteQueryTree(QueryNode node,
        final Map<String, FieldConfig> mappings, String defaultField)
        throws GlobalExceptionHandler {

        if (node instanceof FieldQueryNode) {
            return rewriteFieldQueryNode((FieldQueryNode) node, mappings, defaultField);

        } else if (node instanceof TermRangeQueryNode) {
            return rewriteRangeQueryNode((TermRangeQueryNode) node, mappings);

        } else if (node.getChildren() != null) {
            List<QueryNode> newChildren = new ArrayList<>();
            for (QueryNode childNode : node.getChildren()) {
                newChildren.add(rewriteQueryTree(childNode, mappings, defaultField));
            }
            node.set(newChildren);
        }

        return node;
    }

    private static QueryNode rewriteFieldQueryNode(FieldQueryNode node,
        final Map<String, FieldConfig> mappings, String defaultField)
        throws GlobalExceptionHandler {

        String fieldName = node.getFieldAsString();

        FieldConfig mappedFields;
        try {
            if (fieldName.contains(NESTED_FIELD_DELIMITER)) {
                List<String> nestedNames = Arrays.stream(
                        fieldName.split(NESTED_FIELD_DELIMITER_ESCAPED))
                    .toList();
                Map<String, FieldConfig> innerMostMappings = mappings.get(nestedNames.getFirst())
                    .getObjectMapping();

                if (defaultField == null) {
                    Objects.requireNonNull(innerMostMappings);
                }

                for (int i = 1; i < nestedNames.size() - 1; i++) {
                    mappedFields = innerMostMappings.get(nestedNames.get(i));
                    innerMostMappings = mappedFields.getObjectMapping();
                    if (defaultField == null) {
                        Objects.requireNonNull(innerMostMappings);
                    }
                }
                mappedFields = innerMostMappings.get(nestedNames.getLast());
            } else {
                mappedFields = mappings.get(fieldName);
            }

            if (defaultField == null) {
                Objects.requireNonNull(mappedFields);
            }

        } catch (NullPointerException e) {
            throw new GlobalExceptionHandler("Field not found in mappings: " + fieldName,
                StatusCode.INVALID_INPUT_VALUE);
        }

        // If defaultField exists, treat ':' as space when field not in mappings. else, throw NullPointerException
        if (mappedFields == null) {
            return new FieldQueryNode(defaultField, fieldName + " " + node.getTextAsString(),
                node.getBegin(),
                node.getEnd());
        } else if (mappedFields.getType().equals(DATA_TYPE_TEXT)) {
            List<QueryNode> orNodes = new ArrayList<>();
            for (String mappedAnalyzer : mappedFields.getAnalyzers()) {
                String mappedFieldName = fieldName + NESTED_FIELD_DELIMITER + mappedAnalyzer;
                orNodes.add(
                    new FieldQueryNode(mappedFieldName, node.getTextAsString(), node.getBegin(),
                        node.getEnd()));
            }
            return new OrQueryNode(orNodes);
        }
        return node;
    }

    private static TermRangeQueryNode rewriteRangeQueryNode(TermRangeQueryNode node,
        final Map<String, FieldConfig> mappings) throws GlobalExceptionHandler {

        FieldQueryNode lowerField = (FieldQueryNode) node.getChildren().get(0);
        FieldQueryNode upperField = (FieldQueryNode) node.getChildren().get(1);

        FieldQueryNode lowerNode;
        FieldQueryNode upperNode;
        if (mappings.get(lowerField.getFieldAsString()).getType().equals(DATA_TYPE_DATETIME)) {
            lowerNode = lowerField.getTextAsString().equals(QUERY_SYNTAX_WILDCARD) ? lowerField
                : new FieldQueryNode(lowerField.getFieldAsString(),
                    String.valueOf(stringToTime(lowerField.getTextAsString())),
                    lowerField.getBegin(),
                    lowerField.getEnd());

            upperNode = upperField.getTextAsString().equals(QUERY_SYNTAX_WILDCARD) ? upperField
                : new FieldQueryNode(upperField.getFieldAsString(),
                    String.valueOf(stringToTime(upperField.getTextAsString())),
                    upperField.getBegin(),
                    upperField.getEnd());
        } else {
            lowerNode = lowerField;
            upperNode = upperField;
        }

        return new TermRangeQueryNode(lowerNode, upperNode, node.isLowerInclusive(),
            node.isUpperInclusive()) {
            @Override
            public String toQueryString(EscapeQuerySyntax escaper) {
                String field = lowerNode.getFieldAsString();
                String lowerText = lowerNode.getTextAsString();
                String upperText = upperNode.getTextAsString();
                String lowerBracket = node.isLowerInclusive() ? QUERY_SYNTAX_RANGE_START_INCLUSIVE
                    : QUERY_SYNTAX_RANGE_START_EXCLUSIVE;
                String upperBracket = node.isUpperInclusive() ? QUERY_SYNTAX_RANGE_END_INCLUSIVE
                    : QUERY_SYNTAX_RANGE_END_EXCLUSIVE;

                // Return the formatted string for the range query
                return field + QUERY_SYNTAX_FIELD_DELIMITER + lowerBracket + lowerText
                    + QUERY_SYNTAX_RANGE_DELIMITER + upperText
                    + upperBracket;
            }
        };
    }
}