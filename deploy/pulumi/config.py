import pulumi

# Define variables (these would be provided through config or as stack parameters)
config = pulumi.Config()
aws_config = pulumi.Config("aws")

AWS_REGION = aws_config.require("region")
AWS_ACCOUNT_ID = config.get("AccountId")

ENV = config.get("Env")
RESOURCE_TIMEOUT = config.get("ResourceTimeout")

API_VERSION = config.get("ApiVersion")

VPC_ID = config.get("VpcId")
VPC_SECURITY_GROUP_ID = config.get("VpcSecurityGroupId")
VPC_PRIVATE_SUBNET_ID0 = config.get("VpcPrivateSubnetId0")
VPC_PRIVATE_SUBNET_ID1 = config.get("VpcPrivateSubnetId1")
VPC_PRIVATE_SUBNET_ID2 = config.get("VpcPrivateSubnetId2")

QUERY_EXECUTOR_QUALIFIER = config.get("QueryExecutorQualifier")

INDEX_BUILDER_TIMEOUT = config.get("IndexBuilderTimeout")
INDEX_BUILD_INTERVAL = config.get("IndexBuildInterval")
INDEX_BUILDER_STORAGE_SIZE_GB = config.get_int("IndexBuilderStorageSizeGB")
INDEX_BUILDER_VCPU = config.get("IndexBuilderVcpu")
INDEX_BUILDER_MEMORY_SIZE_MB = config.get("IndexBuilderMemorySizeMB")
INDEX_BUILDER_JOB_NAME_PREFIX = config.get("IndexBuilderJobNamePrefix")
INDEX_BUILDER_MAX_COMPUTE_VCPUS = config.get("IndexBuilderMaxComputeVcpus")
INDEX_BUILDER_CONCURRENCY = config.get("IndexBuilderConcurrency")
INDEX_BUILDER_LOCAL_PATH = config.get("IndexBuilderLocalPath")
INDEX_BUILDER_DOCKER_FILE_PATH = config.get("IndexBuilderDockerFilePath")

FS_TEMP_PATH = config.get("FsTempPath")
MAX_NUM_COMPUTE_NODES_STD = config.get_int("MaxNumComputeNodesStd")
MAX_NUM_COMPUTE_NODES_IA = config.get_int("MaxNumComputeNodesIa")
DESIRED_MEMORY_TO_STORAGE_RATIO_STD = config.get("DesiredMemoryToStorageRatioStd")
DESIRED_MEMORY_TO_STORAGE_RATIO_IA = config.get("DesiredMemoryToStorageRatioIa")
MEMORY_SIZE_PER_COMPUTE_NODE_STD = config.get("MemorySizePerComputeNodeStd")
MEMORY_SIZE_PER_COMPUTE_NODE_IA = config.get("MemorySizePerComputeNodeIa")

FUNCTION_LOCAL_PATH = config.get("FunctionLocalPath")
FUNCTION_S3_KEY = config.get("FunctionS3Key")

DEFAULT_LAMBDA_TIMEOUT = config.get("DefaultLambdaTimeout")
DEFAULT_LAMBDA_STORAGE_SIZE_MB = config.get("DefaultLambdaStorageSizeMB")
DEFAULT_LAMBDA_MEMORY_SIZE_MB = config.get("DefaultLambdaMemorySizeMB")
COMMAND_CONTROLLER_TIMEOUT = config.get("CommandControllerTimeout")
DEFAULT_PREFIX = config.get("DefaultPrefix")

LOG_RETENTION_IN_DAYS = config.get("LogRetentionInDays")
LAMBDA_LOG_FORMAT = config.get("LambdaLogFormat")
LAMBDA_APPLICATION_LOG_LEVEL = config.get("LambdaApplicationLogLevel")
LAMBDA_SYSTEM_LOG_LEVEL = config.get("LambdaSystemLogLevel")

COMMAND_CONTROLLER_QUALIFIER = config.get("CommandControllerQualifier")
REQUEST_CONTROLLER_QUALIFIER = config.get("RequestControllerQualifier")
INDEX_REFRESHER_QUALIFIER = config.get("IndexRefresherQualifier")
QUERY_CONTROLLER_QUALIFIER = config.get("QueryControllerQualifier")
INDEX_BUILDER_RETRY_ATTEMPTS = config.get("IndexBuilderRetryAttempts")

S3_ACL = config.get("S3Acl")
S3_TEMP_BUCKET_LIFECYCLE_RULE_IN_DAYS = config.get_int(
    "S3TempBucketLifecycleRuleInDays"
)
S3_NONCURRENT_VERSION_EXPIRATION_IN_DAYS = config.get_int(
    "S3NoncurrentVersionExpirationInDays"
)
S3_NONCURRENT_VERSION_INTELLIGENT_TIERING_TRANSITION_IN_DAYS = config.get_int(
    "S3NoncurrentVersionInTelligentTieringTransitionInDays"
)
S3_CURRENT_VERSION_INTELLIGENT_TIERING_TRANSITION_IN_DAYS = config.get_int(
    "S3CurrentVersionInTelligentTieringTransitionInDays"
)

SQS_VISIBILITY_TIMEOUT_SECONDS = config.get("SQSVisibilityTimeoutSeconds")

SCHEDULER_MAXIMUM_EVENT_AGE_IN_SECONDS = config.get_int(
    "SchedulerMaximumEventAgeInSeconds"
)
SCHEDULER_MAXIMUM_RETRY_ATTEMPTS = config.get_int("SchedulerMaximumRetryAttempts")

WAL_PAYLOAD_SIZE_THRESHOLD = config.get("WalPayloadSizeThreshold")
BULK_UPSERT_SIZE_LIMIT_MB = config.get("BulkUpsertSizeLimitMb")