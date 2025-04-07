package io.wrtn.infra.aws;

public final class Constants {

    public static final String AWS_REGION = System.getenv("AIMP_AWS_REGION");

    public static class S3 {

        public static final String INDEX_BUCKET = System.getenv("INDEX_BUCKET");
        public static final String TEMP_BUCKET = System.getenv("TEMP_BUCKET");
        public static final int PRESIGNED_URL_EXPIRATION_IN_MINUTES = 30;
    }

    public static class DynamoDB {

        public static final String PROJECT_TABLE_NAME = System.getenv("PROJECT_TABLE_NAME");
        public static final String INDEX_TABLE_NAME = System.getenv("INDEX_TABLE_NAME");
        public static final String DELETED_INDEX_TABLE_NAME = System.getenv(
            "DELETED_INDEX_TABLE_NAME");

    }

    public static class SQS {

        public static final int MAX_DELAY_SECONDS = 900;
        public static final int MIN_DELAY_SECONDS = 0;
    }

    public static class Batch {

        public static final String INDEX_BUILDER_JOB_DEFINITION_NAME = System.getenv(
            "INDEX_BUILDER_JOB_DEFINITION_NAME");
        public static final String INDEX_BUILDER_JOB_QUEUE_NAME = System.getenv(
            "INDEX_BUILDER_JOB_QUEUE_NAME");
        public static final String INDEX_BUILDER_JOB_NAME_PREFIX = System.getenv(
            "INDEX_BUILDER_JOB_NAME_PREFIX");
    }

    public static class ApiGateway {

        public static final String PROJECT_API_KEY_PREFIX = System.getenv(
            "PROJECT_API_KEY_PREFIX");
        public static final String PROJECT_USAGE_PLAN_PREFIX = System.getenv(
            "PROJECT_USAGE_PLAN_PREFIX");
        public static final String API_GATEWAY_NAME = System.getenv("API_GATEWAY_NAME");
        public static final String API_GATEWAY_NAME_PRIVATE = System.getenv("API_GATEWAY_NAME_PRIVATE");
        public static final String API_VERSION = System.getenv("API_VERSION");
        public static final String CONTENT_TYPE = "Content-Type";
        public static final String CONTENT_TYPE_LOWER_CASE = "content-type";
        public static final String APPLICATION_JSON = "application/json";
    }
}
