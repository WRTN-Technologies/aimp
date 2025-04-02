package io.wrtn.util;

public final class Constants {

    public static class Config {

        public static final String ADMIN_API_KEY_SECRET_NAME = System.getenv(
            "ADMIN_API_KEY_SECRET_NAME");
        public static final int PROJECT_ID_LENGTH = 7;
        public static final String CONTROL_QUEUE_URL = System.getenv("CONTROL_QUEUE_URL");
        public static final int DEFAULT_NUM_SHARDS_PER_INDEX = 1;
        public static final int INDEX_BUILDER_TIMEOUT =
            System.getenv("INDEX_BUILDER_TIMEOUT") == null ? -1 : Integer.parseInt(
                System.getenv("INDEX_BUILDER_TIMEOUT"));
        public static final int INDEX_BUILDER_CONCURRENCY =
            System.getenv("INDEX_BUILDER_CONCURRENCY") == null ? -1 : Integer.parseInt(
                System.getenv("INDEX_BUILDER_CONCURRENCY"));
        public static final String QUERY_EXECUTOR_BASE_NAME_STD = System.getenv(
            "QUERY_EXECUTOR_BASE_NAME_STD");
        public static final String QUERY_EXECUTOR_BASE_NAME_IA = System.getenv(
            "QUERY_EXECUTOR_BASE_NAME_IA");
        public static final String QUERY_EXECUTOR_QUALIFIER = System.getenv(
            "QUERY_EXECUTOR_QUALIFIER");
        public static final String INDEX_REFRESHER_NAME = System.getenv(
            "INDEX_REFRESHER_NAME");
        public static final String INDEX_REFRESHER_QUALIFIER = System.getenv(
            "INDEX_REFRESHER_QUALIFIER");
        public static final String REQUEST_CONTROLLER_NAME = System.getenv(
            "REQUEST_CONTROLLER_NAME");
        public static final String REQUEST_CONTROLLER_QUALIFIER = System.getenv(
            "REQUEST_CONTROLLER_QUALIFIER");
        public static final int INDEX_BUILD_INTERVAL =
            System.getenv("INDEX_BUILD_INTERVAL") == null ? -1 : Integer.parseInt(
                System.getenv("INDEX_BUILD_INTERVAL"));
        public static final int TEMPORARY_SHARD_ID = 0;
        public static final String FS_TEMP_PATH = System.getenv("FS_TEMP_PATH");

        public static final int MAX_NUM_COMPUTE_NODES_STD =
            System.getenv("MAX_NUM_COMPUTE_NODES_STD") == null ? -1 : Integer.parseInt(
                System.getenv("MAX_NUM_COMPUTE_NODES_STD"));
        public static final int MAX_NUM_COMPUTE_NODES_IA =
            System.getenv("MAX_NUM_COMPUTE_NODES_IA") == null ? -1 : Integer.parseInt(
                System.getenv("MAX_NUM_COMPUTE_NODES_IA"));
        public static final float DESIRED_MEMORY_TO_STORAGE_RATIO_STD =
            System.getenv("DESIRED_MEMORY_TO_STORAGE_RATIO_STD") == null ? -1.0f :
                Float.parseFloat(System.getenv("DESIRED_MEMORY_TO_STORAGE_RATIO_STD"));
        public static final float DESIRED_MEMORY_TO_STORAGE_RATIO_IA =
            System.getenv("DESIRED_MEMORY_TO_STORAGE_RATIO_IA") == null ? -1.0f :
                Float.parseFloat(System.getenv("DESIRED_MEMORY_TO_STORAGE_RATIO_IA"));
        public static final int MEMORY_SIZE_PER_COMPUTE_NODE_STD =
            System.getenv("MEMORY_SIZE_PER_COMPUTE_NODE_STD") == null ? -1 : Integer.parseInt(
                System.getenv("MEMORY_SIZE_PER_COMPUTE_NODE_STD"));
        public static final int MEMORY_SIZE_PER_COMPUTE_NODE_IA =
            System.getenv("MEMORY_SIZE_PER_COMPUTE_NODE_IA") == null ? -1 : Integer.parseInt(
                System.getenv("MEMORY_SIZE_PER_COMPUTE_NODE_IA"));
        public static final long WAL_PAYLOAD_SIZE_THRESHOLD =
            System.getenv("WAL_PAYLOAD_SIZE_THRESHOLD") == null ? -1 : Long.parseLong(
                System.getenv("WAL_PAYLOAD_SIZE_THRESHOLD"));
        public static final String EFS_MOUNT_PATH = System.getenv("EFS_MOUNT_PATH");
        public static final int DEFAULT_LAMBDA_TIMEOUT =
            System.getenv("DEFAULT_LAMBDA_TIMEOUT") == null ? -1 : Integer.parseInt(
                System.getenv("DEFAULT_LAMBDA_TIMEOUT"));
        public static final long BULK_UPSERT_SIZE_LIMIT_MB =
            System.getenv("BULK_UPSERT_SIZE_LIMIT_MB") == null ? -1 : Long.parseLong(
                System.getenv("BULK_UPSERT_SIZE_LIMIT_MB")) * 1024 * 1024;
    }

    public static class CommandType {

        public static final String PROJECT_CREATE = "PROJECT_CREATE";
        public static final String PROJECT_DELETE = "PROJECT_DELETE";
        public static final String INDEX_CREATE = "INDEX_CREATE";
        public static final String INDEX_DELETE = "INDEX_DELETE";
        public static final String INDEX_BUILD = "INDEX_BUILD";
        public static final String INDEX_RESCHED = "INDEX_RESCHED";
        public static final String DOCUMENT_UPSERT = "DOCUMENT_UPSERT";
        public static final String DOCUMENT_DELETE = "DOCUMENT_DELETE";
        public static final String DOCUMENT_QUERY = "QUERY";
        public static final String DOCUMENT_FETCH = "FETCH";
    }

    public static class Limits {

        public static final int MAX_DOCUMENTS_TO_FETCH = 1000;
        public static final int MAX_DOCUMENTS_TO_QUERY = 1000;
    }

    public static class HttpMethod {

        public static final String GET = "GET";
        public static final String POST = "POST";
        public static final String DELETE = "DELETE";
        public static final String PATCH = "PATCH";
        public static final String PUT = "PUT";
    }

    public static class ProjectStatus {

        public static final String PROJECT_STATUS_CREATING = "CREATING";
        public static final String PROJECT_STATUS_ACTIVE = "ACTIVE";
        public static final String PROJECT_STATUS_DELETING = "DELETING";
    }

    public static class IndexStatus {

        public static final String INDEX_STATUS_CREATING = "CREATING";
        public static final String INDEX_STATUS_ACTIVE = "ACTIVE";
        public static final String INDEX_STATUS_DELETING = "DELETING";
    }

    public static class IndexClass {

        public static final String INDEX_CLASS_STD = "STANDARD";
        public static final String INDEX_CLASS_IA = "INFREQUENT_ACCESS";
        public static final String INDEX_CLASS_DEFAULT = INDEX_CLASS_STD;
    }

}
