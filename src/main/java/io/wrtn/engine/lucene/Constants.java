package io.wrtn.engine.lucene;

import java.util.Arrays;
import java.util.List;

public final class Constants {

    public static final long RAM_BUFFER_SIZE_MB =
        System.getenv("RAM_BUFFER_SIZE_MB") == null ? -1 : Long.parseLong(
            System.getenv("RAM_BUFFER_SIZE_MB"));

    public static final short MAX_DIMS_COUNT = 1024;
    public static final short DEFAULT_MAX_CLAUSE_COUNT = 1024;
    public static final String DOC_FIELD_ID = "id";
    public static final String DOC_FIELD_INTERNAL_WAL_ID = "_walId";
    public static final String DOC_FIELD_INTERNAL_DOCUMENT = "_doc";
    public static final String VECTOR_VALUE_SUFFIX = "_values";
    public static final String SORT_FIELD_SUFFIX = "_docValues";
    public static final String NESTED_FIELD_DELIMITER = ".";
    public static final String NESTED_FIELD_DELIMITER_ESCAPED = "\\.";

    public static final List<String> RESERVED_FIELD_NAMES = Arrays.asList(DOC_FIELD_ID,
        DOC_FIELD_INTERNAL_WAL_ID, DOC_FIELD_INTERNAL_DOCUMENT, VECTOR_VALUE_SUFFIX,
        NESTED_FIELD_DELIMITER);

    public static final List<String> RESERVED_FIELD_NAMES_FOR_VALIDATE = Arrays.asList(
        DOC_FIELD_INTERNAL_WAL_ID, DOC_FIELD_INTERNAL_DOCUMENT, VECTOR_VALUE_SUFFIX,
        NESTED_FIELD_DELIMITER);

    public static final List<String> VECTOR_SIMILARITY_ARRAY = Arrays.asList("cosine",
        "dot_product", "l2_norm", "max_inner_product");

    public static final String DATA_TYPE_VECTOR = "vector";
    public static final String DATA_TYPE_TEXT = "text";
    public static final String DATA_TYPE_KEYWORD = "keyword";
    public static final String DATA_TYPE_LONG = "long";
    public static final String DATA_TYPE_DOUBLE = "double";
    public static final String DATA_TYPE_BOOLEAN = "boolean";
    public static final String DATA_TYPE_BOOLEAN_TRUE = "true";
    public static final String DATA_TYPE_BOOLEAN_FALSE = "false";
    public static final List<String> BOOLEAN_VALUES = Arrays.asList(DATA_TYPE_BOOLEAN_TRUE,
        DATA_TYPE_BOOLEAN_FALSE);
    public static final String DATA_TYPE_DATETIME = "datetime";
    public static final String DATA_TYPE_OBJECT = "object";
    public static final List<String> SUPPORTED_SORT_FIELDS = Arrays.asList(DATA_TYPE_LONG,
        DATA_TYPE_DOUBLE, DATA_TYPE_DATETIME, DATA_TYPE_KEYWORD);
    public static final String SORT_DIRECTION_ASC = "asc";
    public static final String SORT_DIRECTION_DESC = "desc";
    public static final List<String> SUPPORTED_SORT_DIRECTIONS = Arrays.asList(SORT_DIRECTION_ASC,
        SORT_DIRECTION_DESC);

    public static final String VECTOR_SIMILARITY_L2_NORM = "l2_norm";
    public static final String VECTOR_SIMILARITY_L2_NORM_INTERNAL = "euclidean";
    public static final String VECTOR_SIMILARITY_COSINE = "cosine";
    public static final String VECTOR_SIMILARITY_DOT_PRODUCT = "dot_product";
    public static final String VECTOR_SIMILARITY_MAX_INNER_PRODUCT = "max_inner_product";
    public static final String VECTOR_SIMILARITY_DEFAULT = VECTOR_SIMILARITY_COSINE;

    public static final String ANALYZER_TYPE_KOREAN = "korean"; // nori
    public static final String ANALYZER_TYPE_JAPANESE = "japanese"; // kuromoji
    public static final String ANALYZER_TYPE_ENGLISH = "english";
    public static final String ANALYZER_TYPE_STANDARD = "standard";
    public static final String ANALYZER_TYPE_DEFAULT = ANALYZER_TYPE_STANDARD;

    public static final String QUERY_TYPE_QUERY = "query";
    public static final String QUERY_TYPE_BOOL = "bool";
    public static final String QUERY_TYPE_KNN = "knn";
    public static final String QUERY_TYPE_QUERY_STRING = "queryString";
    public static final String QUERY_TYPE_BOOST = "boost";

    public static final String QUERY_PARAM_DEFAULT_FIELD = "defaultField";
    public static final String QUERY_PARAM_OCCUR = "occur";
    public static final String QUERY_PARAM_FILTER = "filter";
    public static final String QUERY_PARAM_TOP_K = "k";
    public static final String QUERY_PARAM_VECTOR = "queryVector";
    public static final String QUERY_PARAM_QUERY_VECTOR_FIELD = "field";
    public static final String QUERY_PARAM_NUM_CANDIDATES = "numCandidates";
    public static final String QUERY_PARAM_SKIP_SYNTAX = "skipSyntax";
    public static final int NUM_CANDIDATES_LIMIT = 5000;


    public static final String QUERY_ENUM_MUST = "MUST";
    public static final String QUERY_ENUM_MUST_NOT = "MUST_NOT";
    public static final String QUERY_ENUM_SHOULD = "SHOULD";

    public static final String QUERY_SYNTAX_WILDCARD = "*";
    public static final String QUERY_SYNTAX_MATCH_ALL = "*:*";
    public static final String QUERY_SYNTAX_RANGE_START_INCLUSIVE = "[";
    public static final String QUERY_SYNTAX_RANGE_END_INCLUSIVE = "]";
    public static final String QUERY_SYNTAX_RANGE_START_EXCLUSIVE = "{";
    public static final String QUERY_SYNTAX_RANGE_END_EXCLUSIVE = "}";
    public static final String QUERY_SYNTAX_FIELD_DELIMITER = ":";
    public static final String QUERY_SYNTAX_RANGE_DELIMITER = " TO ";

    public static final String KEYWORD_IGNORED_SUFFIX = "ignored";
    public static final int DEFAULT_IGNORE_ABOVE = 4096;

    public static final String STORAGE_META_FILE_NAME = "meta.json";

    public static final int MAX_STORAGE_REQUEST_AT_ONCE = 100;

    public static final int MIN_DOCS_SIZE_MB_FOR_INDEX_BUILD_CONCURRENCY = 1;
    public static final int MIN_NUM_DOCS_FOR_INDEX_BUILD_CONCURRENCY = 1000;
}
