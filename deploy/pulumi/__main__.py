import pulumi
import pulumi_aws as aws
import pulumi_docker_build as docker_build
import pulumi_std as std
import json
import pulumi_aws_apigateway as apigateway

from config import (
    ENV,
    VPC_ID,
    VPC_SECURITY_GROUP_ID,
    VPC_PRIVATE_SUBNET_ID0,
    VPC_PRIVATE_SUBNET_ID1,
    VPC_PRIVATE_SUBNET_ID2,
    FUNCTION_LOCAL_PATH,
    INDEX_BUILDER_LOCAL_PATH,
    INDEX_BUILDER_DOCKER_FILE_PATH,
    INDEX_BUILDER_VCPU,
    INDEX_BUILDER_MEMORY_SIZE_MB,
    INDEX_BUILDER_STORAGE_SIZE_GB,
    AWS_REGION,
    QUERY_EXECUTOR_QUALIFIER,
    SCHEDULER_MAXIMUM_EVENT_AGE_IN_SECONDS,
    SCHEDULER_MAXIMUM_RETRY_ATTEMPTS,
    SQS_VISIBILITY_TIMEOUT_SECONDS,
    LOG_RETENTION_IN_DAYS,
    DEFAULT_LAMBDA_MEMORY_SIZE_MB,
    DEFAULT_LAMBDA_TIMEOUT,
    REQUEST_CONTROLLER_QUALIFIER,
    QUERY_CONTROLLER_QUALIFIER,
    COMMAND_CONTROLLER_QUALIFIER,
    COMMAND_CONTROLLER_TIMEOUT,
    MAX_NUM_COMPUTE_NODES_STD,
    MAX_NUM_COMPUTE_NODES_IA,
    INDEX_BUILDER_RETRY_ATTEMPTS,
    INDEX_BUILDER_MAX_COMPUTE_VCPUS,
    INDEX_REFRESHER_QUALIFIER,
    API_VERSION,
    AWS_ACCOUNT_ID,
    FUNCTION_S3_KEY
)

from variables import (
    SCHEDULER_ROLE_NAME,
    SCHEDULER_POLICY_NAME,
    BASE_QUEUE_RESOURCES,
    BASE_LAMBDA_RESOURCES,
    REQUEST_CONTROLLER_ROLE_NAME,
    REQUEST_CONTROLLER_POLICY_NAME,
    COMMAND_CONTROLLER_ROLE_NAME,
    COMMAND_CONTROLLER_POLICY_NAME,
    QUERY_EXECUTOR_ROLE_NAME,
    QUERY_EXECUTOR_POLICY_NAME,
    INDEX_BUILDER_ROLE_NAME,
    INDEX_BUILDER_POLICY_NAME,
    BILLING_MODE,
    DELETION_PROTECTION,
    PROJECT_HASH_KEY,
    PROJECT_ATTRIBUTES_CONFIG,
    INDEX_HASH_KEY,
    INDEX_RANGE_KEY,
    INDEX_ATTRIBUTES_CONFIG,
    DELETED_INDEX_HASH_KEY,
    DELETED_INDEX_ATTRIBUTES_CONFIG,
    PROJECTS_TABLE_NAME,
    INDEXES_TABLE_NAME,
    DELETED_INDEXES_TABLE_NAME,
    SECRET_NAME,
    SECRET_VERSION_NAME,
    BUCKET_NAME,
    BUCKET_OWNER_NAME,
    S3_ACL_CONFIG,
    S3_VERSION_CONFIG,
    S3_DEFAULT_VERSION_CONFIG,
    S3_DEFAULT_OWNER_RULE_CONFIG,
    BUCKET_LIFECYCLE_NAME,
    TEMP_BUCKET_LIFECYCLE_NAME,
    S3_BUCKET_LIFECYCLE_RULE,
    S3_TEMP_BUCKET_LIFECYCLE_RULE,
    TEMP_BUCKET_NAME,
    TEMP_BUCKET_OWNER_NAME,
    CONTROL_QUEUE_NAME,
    INDEX_BUILD_SCHEDULE_NAME,
    INDEX_RESCHED_SCHEDULE_NAME,
    SCHEDULE_GROUP_NAME,
    FLEXIBLE_TIME_WINDOW_OFF_MODE,
    DEFAULT_SCHEDULE_EXPRESSION,
    REQUEST_CONTROLLER_NAME,
    REQUEST_CONTROLLER_ALIAS_NAME,
    REQUEST_CONTROLLER_LOG_GROUP_NAME,
    QUERY_CONTROLLER_NAME,
    QUERY_CONTROLLER_ROLE_NAME,
    QUERY_CONTROLLER_ALIAS_NAME,
    QUERY_CONTROLLER_POLICY_NAME,
    QUERY_CONTROLLER_LOG_GROUP_NAME,
    INDEX_REFRESHER_NAME,
    INDEX_REFRESHER_ALIAS_NAME,
    INDEX_REFRESHER_LOG_GROUP_NAME,
    INDEX_REFRESHER_ROLE_NAME,
    INDEX_REFRESHER_POLICY_NAME,
    COMMAND_CONTROLLER_NAME,
    COMMAND_CONTROLLER_ALIAS_NAME,
    COMMAND_CONTROLLER_LOG_GROUP_NAME,
    DEFAULT_STORAGE_CONFIG,
    LAMBDA_RUNTIME_CONFIG,
    LOG_CONFIG,
    QUERY_EXECUTOR_NAME_STD,
    QUERY_EXECUTOR_NAME_IA,
    QUERY_EXECUTOR_ALIAS_NAME_STD,
    QUERY_EXECUTOR_ALIAS_NAME_IA,
    QUERY_EXECUTOR_LOG_GROUP_NAME_STD,
    QUERY_EXECUTOR_LOG_GROUP_NAME_IA,
    INDEX_BUILDER_JOB_DEFINITION_NAME,
    INDEX_BUILDER_TIMEOUT,
    INDEX_BUILDER_JOB_QUEUE_NAME,
    CONTROL_QUEUE_EVENT_SOURCE_MAPPING_NAME,
    INDEX_BUILDER_ECR_REPO_NAME,
    INDEX_BUILDER_ECR_LIFE_CYCLE_POLICY_NAME,
    INDEX_BUILDER_ECR_IMAGE_NAME,
    INDEX_BUILDER_LOG_GROUP_NAME,
    INDEX_BUILDER_NAME,
    INDEX_BUILDER_COMPUTE_ENVIRONMENT_NAME,
    BATCH_ENVIRONMENT_CONFIG,
    API_GATEWAY_NAME,
    API_GATEWAY_NAME_PRIVATE,
    API_GATEWAY_LOG_GROUP_NAME,
    API_GATEWAY_ENDPOINT_NAME,
    EFS_NAME,
    EFS_MOUNT_TARGET_NAME0,
    EFS_MOUNT_TARGET_NAME1,
    EFS_MOUNT_TARGET_NAME2,
    EFS_ACCESS_POINT_NAME,
    EFS_ROOT_PATH,
    EFS_MOUNT_PATH,
    QUERY_EXECUTOR_ENV,
    INDEX_REFRESHER_ENV,
    COMMAND_CONTROLLER_ENV,
    REQUEST_CONTROLLER_ENV,
    QUERY_CONTROLLER_ENV,
    ADMIN_API_KEY_NAME,
    ADMIN_USAGE_PLAN_NAME,
    ADMIN_USAGE_PLAN_KEY_NAME,
    ROUTES,
    QUERY_ROUTES,
    LAMBDA_ARM_ARCHITECTURE,
    BATCH_X86_ARCHITECTURE,
    FUNCTION_OBJECT_NAME,
)

def query_executor(id, index_class):
    id = str(id)
    executor_name = ""
    log_group_name = ""
    alias_name = ""
    if index_class == "std":
        executor_name = QUERY_EXECUTOR_NAME_STD
        log_group_name = QUERY_EXECUTOR_LOG_GROUP_NAME_STD
        alias_name = QUERY_EXECUTOR_ALIAS_NAME_STD
    elif index_class == "ia":
        executor_name = QUERY_EXECUTOR_NAME_IA
        log_group_name = QUERY_EXECUTOR_LOG_GROUP_NAME_IA
        alias_name = QUERY_EXECUTOR_ALIAS_NAME_IA

    log_group = aws.cloudwatch.LogGroup(
        f"{log_group_name}-{id}",
        name=f"/aws/lambda/{executor_name}-{id}",
        retention_in_days=LOG_RETENTION_IN_DAYS,
    )

    executor = aws.lambda_.Function(
        f"{executor_name}-{id}",
        name=f"{executor_name}-{id}",
        s3_bucket=s3_bucket.id,
        s3_key=function_object.key,
        role=query_executor_role.arn,
        handler="io.wrtn.lambda.QueryExecutor::handleRequest",
        source_code_hash=function_object.source_hash,
        runtime=LAMBDA_RUNTIME_CONFIG,
        architectures=[LAMBDA_ARM_ARCHITECTURE],
        memory_size=DEFAULT_LAMBDA_MEMORY_SIZE_MB,
        ephemeral_storage=DEFAULT_STORAGE_CONFIG,
        timeout=DEFAULT_LAMBDA_TIMEOUT,
        environment=get_lambda_environment_config(QUERY_EXECUTOR_QUALIFIER),
        vpc_config=aws.lambda_.FunctionVpcConfigArgs(
            security_group_ids=[VPC_SECURITY_GROUP_ID],
            subnet_ids=[
                VPC_PRIVATE_SUBNET_ID0,
                VPC_PRIVATE_SUBNET_ID1,
                VPC_PRIVATE_SUBNET_ID2,
            ],
        ),
        logging_config=LOG_CONFIG,
        publish=True,
        opts=pulumi.ResourceOptions(
            depends_on=[
                log_group,
                query_executor_role,
                function_object
            ],
        ),
    )

    aws.lambda_.Alias(
        f"{alias_name}-{id}",
        function_name=executor.name,
        function_version=executor.version,
        name=QUERY_EXECUTOR_QUALIFIER,
        opts=pulumi.ResourceOptions(
            depends_on=[executor],
        ),
    )

def get_lambda_environment_config(lambda_qualifier):
    if lambda_qualifier == QUERY_EXECUTOR_QUALIFIER:
        return QUERY_EXECUTOR_ENV
    elif lambda_qualifier == INDEX_REFRESHER_QUALIFIER:
        return INDEX_REFRESHER_ENV
    elif lambda_qualifier == COMMAND_CONTROLLER_QUALIFIER:
        return COMMAND_CONTROLLER_ENV
    elif lambda_qualifier == REQUEST_CONTROLLER_QUALIFIER:
        return REQUEST_CONTROLLER_ENV
    elif lambda_qualifier == QUERY_CONTROLLER_QUALIFIER:
        return QUERY_CONTROLLER_ENV


def make_container_properties():
    return pulumi.Output.all(
        repository_url=index_builder_ecr_repo.repository_url,
        index_builder_log_group_name=index_builder_log_group.name,
        index_builder_role_arn=index_builder_role.arn,
        efs_id=efs.id,
        efs_access_point_id=efs_access_point.id,
    ).apply(
        lambda args: json.dumps(
            {
                "command": [
                    "java",
                    "-cp",
                    "aimp.jar",
                    "io.wrtn.lambda.IndexBuilder",
                    "--event",
                    "Ref::event",
                ],
                "environment": BATCH_ENVIRONMENT_CONFIG,
                "ephemeralStorage": {"sizeInGiB": INDEX_BUILDER_STORAGE_SIZE_GB},
                "jobRoleArn": args["index_builder_role_arn"],
                "executionRoleArn": args["index_builder_role_arn"],
                "fargatePlatformConfiguration": {"platformVersion": "LATEST"},
                "image": f"{args['repository_url']}:{ENV}",
                "logConfiguration": {
                    "secretOptions": [],
                    "logDriver": "awslogs",
                    "options": {"awslogs-group": args["index_builder_log_group_name"]},
                },
                "resourceRequirements": [
                    {"type": "VCPU", "value": INDEX_BUILDER_VCPU},
                    {"type": "MEMORY", "value": INDEX_BUILDER_MEMORY_SIZE_MB},
                ],
                "runtimePlatform": {
                    "cpuArchitecture": BATCH_X86_ARCHITECTURE,
                    "operatingSystemFamily": "LINUX",
                },
                "mountPoints": [
                    {
                        "sourceVolume": "myEfsVolume",
                        "containerPath": EFS_MOUNT_PATH,
                    }
                ],
                "volumes": [
                    {
                        "name": "myEfsVolume",
                        "efsVolumeConfiguration": {
                            "fileSystemId": args["efs_id"],
                            "transitEncryption": "ENABLED",
                            "authorizationConfig": {
                                "accessPointId": args["efs_access_point_id"],
                                "iam": "ENABLED",
                            },
                        },
                    }
                ],
            }
        )
    )

# Create admin API key and usage plan
admin_api_key = aws.apigateway.ApiKey(
    ADMIN_API_KEY_NAME,
    enabled=True,
    name=ADMIN_API_KEY_NAME,
)

# Secret Manager
secret = aws.secretsmanager.Secret(
    SECRET_NAME,
    name=SECRET_NAME,
    force_overwrite_replica_secret=True,
    recovery_window_in_days=0,
    description=f"This is a AIMP secret for admin credentials for {ENV} environment.",
)

secret_version = aws.secretsmanager.SecretVersion(
    SECRET_VERSION_NAME,
    secret_id=secret.id,
    secret_string=admin_api_key.value,
    opts=pulumi.ResourceOptions(depends_on=[secret, admin_api_key]),
)

# DynamoDB tables
projects_table = aws.dynamodb.Table(
    PROJECTS_TABLE_NAME,
    name=PROJECTS_TABLE_NAME,
    hash_key=PROJECT_HASH_KEY,
    billing_mode=BILLING_MODE,
    attributes=PROJECT_ATTRIBUTES_CONFIG,
    deletion_protection_enabled=DELETION_PROTECTION,
)

indexes_table = aws.dynamodb.Table(
    INDEXES_TABLE_NAME,
    name=INDEXES_TABLE_NAME,
    hash_key=INDEX_HASH_KEY,
    range_key=INDEX_RANGE_KEY,
    billing_mode=BILLING_MODE,
    attributes=INDEX_ATTRIBUTES_CONFIG,
    deletion_protection_enabled=DELETION_PROTECTION,
)

deleted_indexes_table = aws.dynamodb.Table(
    DELETED_INDEXES_TABLE_NAME,
    name=DELETED_INDEXES_TABLE_NAME,
    hash_key=DELETED_INDEX_HASH_KEY,
    billing_mode=BILLING_MODE,
    attributes=DELETED_INDEX_ATTRIBUTES_CONFIG,
    deletion_protection_enabled=DELETION_PROTECTION,
)

# S3
s3_bucket = aws.s3.Bucket(
    BUCKET_NAME,
    acl=S3_ACL_CONFIG,
    bucket=BUCKET_NAME,
    versioning=S3_VERSION_CONFIG,
    force_destroy=(ENV != "prod" or ENV != "cold"),
)

s3_bucket_ownership_controls = aws.s3.BucketOwnershipControls(
    BUCKET_OWNER_NAME,
    bucket=s3_bucket.id,
    rule=S3_DEFAULT_OWNER_RULE_CONFIG,
    opts=pulumi.ResourceOptions(depends_on=[s3_bucket]),
)

s3_bucket_config = aws.s3.BucketLifecycleConfigurationV2(
    BUCKET_LIFECYCLE_NAME,
    bucket=s3_bucket.id,
    rules=S3_BUCKET_LIFECYCLE_RULE,
    opts=pulumi.ResourceOptions(depends_on=[s3_bucket]),
)

s3_temp_bucket = aws.s3.Bucket(
    TEMP_BUCKET_NAME,
    acl=S3_ACL_CONFIG,
    bucket=TEMP_BUCKET_NAME,
    versioning=S3_DEFAULT_VERSION_CONFIG,
    force_destroy=True,
)

s3_temp_bucket_ownership_controls = aws.s3.BucketOwnershipControls(
    TEMP_BUCKET_OWNER_NAME,
    bucket=s3_temp_bucket.id,
    rule=S3_DEFAULT_OWNER_RULE_CONFIG,
    opts=pulumi.ResourceOptions(depends_on=[s3_temp_bucket]),
)

s3_temp_bucket_config = aws.s3.BucketLifecycleConfigurationV2(
    TEMP_BUCKET_LIFECYCLE_NAME,
    bucket=s3_temp_bucket.id,
    rules=S3_TEMP_BUCKET_LIFECYCLE_RULE,
    opts=pulumi.ResourceOptions(depends_on=[s3_temp_bucket]),
)

# SQS
control_queue = aws.sqs.Queue(
    CONTROL_QUEUE_NAME,
    name=CONTROL_QUEUE_NAME,
    fifo_queue=False,
    visibility_timeout_seconds=SQS_VISIBILITY_TIMEOUT_SECONDS,
)

# IAM roles
scheduler_role = aws.iam.Role(
    SCHEDULER_ROLE_NAME,
    assume_role_policy=json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Sid": "",
                    "Principal": {"Service": "scheduler.amazonaws.com"},
                }
            ],
        }
    ),
    name=SCHEDULER_ROLE_NAME,
)

scheduler_policy = aws.iam.RolePolicy(
    SCHEDULER_POLICY_NAME,
    name=SCHEDULER_POLICY_NAME,
    role=scheduler_role.id,
    policy=json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Sid": "SendMessageToControlQueue",
                    "Effect": "Allow",
                    "Action": [
                        "sqs:SendMessage",
                    ],
                    "Resource": BASE_QUEUE_RESOURCES,
                },
            ],
        }
    ),
    opts=pulumi.ResourceOptions(parent=scheduler_role, depends_on=[scheduler_role]),
)

request_controller_role = aws.iam.Role(
    REQUEST_CONTROLLER_ROLE_NAME,
    assume_role_policy=json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Sid": "",
                    "Principal": {"Service": "lambda.amazonaws.com"},
                }
            ],
        }
    ),
    name=REQUEST_CONTROLLER_ROLE_NAME,
)

request_controller_policy = aws.iam.RolePolicy(
    REQUEST_CONTROLLER_POLICY_NAME,
    name=REQUEST_CONTROLLER_POLICY_NAME,
    role=request_controller_role.id,
    policy=pulumi.Output.all(
        PROJECT_TABLE_ARN=projects_table.arn,
        INDEX_TABLE_ARN=indexes_table.arn,
        SECRET_MANAGER_ARN=secret.arn,
        S3_BUCKET_ARN=s3_bucket.arn,
        S3_TEMP_BUCKET_ARN=s3_temp_bucket.arn,
    ).apply(
        lambda args: json.dumps(
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "FullAccessForProjectsTable",
                        "Effect": "Allow",
                        "Action": [
                            "dynamodb:BatchGetItem",
                            "dynamodb:BatchWriteItem",
                            "dynamodb:ConditionCheckItem",
                            "dynamodb:PutItem",
                            "dynamodb:DescribeTable",
                            "dynamodb:DeleteItem",
                            "dynamodb:GetItem",
                            "dynamodb:Scan",
                            "dynamodb:Query",
                            "dynamodb:UpdateItem",
                            "dynamodb:GetRecords",
                        ],
                        "Resource": [
                            f"{args['PROJECT_TABLE_ARN']}",
                            f"{args['PROJECT_TABLE_ARN']}/stream/*",
                            f"{args['PROJECT_TABLE_ARN']}/index/*",
                        ],
                    },
                    {
                        "Sid": "FullAccessForIndexesTable",
                        "Effect": "Allow",
                        "Action": [
                            "dynamodb:BatchGetItem",
                            "dynamodb:BatchWriteItem",
                            "dynamodb:ConditionCheckItem",
                            "dynamodb:PutItem",
                            "dynamodb:DescribeTable",
                            "dynamodb:DeleteItem",
                            "dynamodb:GetItem",
                            "dynamodb:Scan",
                            "dynamodb:Query",
                            "dynamodb:UpdateItem",
                            "dynamodb:GetRecords",
                        ],
                        "Resource": [
                            f"{args['INDEX_TABLE_ARN']}",
                            f"{args['INDEX_TABLE_ARN']}/stream/*",
                            f"{args['INDEX_TABLE_ARN']}/index/*",
                        ],
                    },
                    {
                        "Effect": "Allow",
                        "Action": ["dynamodb:ListTables", "dynamodb:DescribeLimits"],
                        "Resource": "*",
                    },
                    {
                        "Effect": "Allow",
                        "Action": "secretsmanager:GetSecretValue",
                        "Resource": f"{args['SECRET_MANAGER_ARN']}",
                    },
                    {
                        "Sid": "AWSLambdaVPCAccessExecutionRole",
                        "Effect": "Allow",
                        "Action": [
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents",
                            "ec2:CreateNetworkInterface",
                            "ec2:DescribeNetworkInterfaces",
                            "ec2:DeleteNetworkInterface",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "SendMessageToSQSControlQueue",
                        "Effect": "Allow",
                        "Action": ["sqs:SendMessage", "sqs:GetQueueUrl"],
                        "Resource": BASE_QUEUE_RESOURCES,
                    },
                    {
                        "Sid": "AmazonElasticFileSystemClientFullAccess",
                        "Effect": "Allow",
                        "Action": [
                            "elasticfilesystem:ClientMount",
                            "elasticfilesystem:ClientWrite",
                            "elasticfilesystem:ClientRootAccess",
                            "elasticfilesystem:DescribeMountTargets",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AccessToS3Bucket",
                        "Effect": "Allow",
                        "Action": [
                            "s3:ListBucket*",
                            "s3:GetObject*",
                            "s3:PutObject*",
                            "s3:DeleteObject*",
                        ],
                        "Resource": [
                            f"{args['S3_TEMP_BUCKET_ARN']}",
                            f"{args['S3_TEMP_BUCKET_ARN']}/*",
                            f"{args['S3_BUCKET_ARN']}",
                            f"{args['S3_BUCKET_ARN']}/*",
                        ],
                    },
                    {
                        "Sid": "AllowInvokeLambdaFunction",
                        "Effect": "Allow",
                        "Action": [
                            "lambda:InvokeFunction",
                            "lambda:GetFunctionConfiguration",
                        ],
                        "Resource": BASE_LAMBDA_RESOURCES,
                    },
                    {
                        "Sid": "AWSApiGatewayAccess",
                        "Effect": "Allow",
                        "Action": [
                            "apigateway:GET",
                            "apigateway:POST",
                            "apigateway:PUT",
                            "apigateway:DELETE",
                            "apigateway:PATCH",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AWSXRayDaemonWriteAccess",
                        "Effect": "Allow",
                        "Action": [
                            "xray:PutTraceSegments",
                            "xray:PutTelemetryRecords",
                            "xray:GetSamplingRules",
                            "xray:GetSamplingTargets",
                            "xray:GetSamplingStatisticSummaries",
                        ],
                        "Resource": ["*"],
                    },
                ],
            }
        ),
    ),
    opts=pulumi.ResourceOptions(
        parent=request_controller_role,
        depends_on=[
            request_controller_role,
            projects_table,
            indexes_table,
            secret,
        ],
    ),
)

query_controller_role = aws.iam.Role(
    QUERY_CONTROLLER_ROLE_NAME,
    assume_role_policy=json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Sid": "",
                    "Principal": {"Service": "lambda.amazonaws.com"},
                }
            ],
        }
    ),
    name=QUERY_CONTROLLER_ROLE_NAME,
)

query_controller_policy = aws.iam.RolePolicy(
    QUERY_CONTROLLER_POLICY_NAME,
    name=QUERY_CONTROLLER_POLICY_NAME,
    role=query_controller_role.id,
    policy=pulumi.Output.all(
        INDEX_TABLE_ARN=indexes_table.arn,
        S3_BUCKET_ARN=s3_bucket.arn,
        S3_TEMP_BUCKET_ARN=s3_temp_bucket.arn,
    ).apply(
        lambda args: json.dumps(
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "FullAccessForIndexesTable",
                        "Effect": "Allow",
                        "Action": [
                            "dynamodb:BatchGetItem",
                            "dynamodb:BatchWriteItem",
                            "dynamodb:ConditionCheckItem",
                            "dynamodb:PutItem",
                            "dynamodb:DescribeTable",
                            "dynamodb:DeleteItem",
                            "dynamodb:GetItem",
                            "dynamodb:Scan",
                            "dynamodb:Query",
                            "dynamodb:UpdateItem",
                            "dynamodb:GetRecords",
                        ],
                        "Resource": [
                            f"{args['INDEX_TABLE_ARN']}",
                            f"{args['INDEX_TABLE_ARN']}/stream/*",
                            f"{args['INDEX_TABLE_ARN']}/index/*",
                        ],
                    },
                    {
                        "Effect": "Allow",
                        "Action": ["dynamodb:ListTables", "dynamodb:DescribeLimits"],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AWSLambdaVPCAccessExecutionRole",
                        "Effect": "Allow",
                        "Action": [
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents",
                            "ec2:CreateNetworkInterface",
                            "ec2:DescribeNetworkInterfaces",
                            "ec2:DeleteNetworkInterface",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AccessToS3Bucket",
                        "Effect": "Allow",
                        "Action": [
                            "s3:ListBucket*",
                            "s3:GetObject*",
                            "s3:PutObject*",
                            "s3:DeleteObject*",
                        ],
                        "Resource": [
                            f"{args['S3_TEMP_BUCKET_ARN']}",
                            f"{args['S3_TEMP_BUCKET_ARN']}/*",
                            f"{args['S3_BUCKET_ARN']}",
                            f"{args['S3_BUCKET_ARN']}/*",
                        ],
                    },
                    {
                        "Sid": "AllowInvokeLambdaFunction",
                        "Effect": "Allow",
                        "Action": [
                            "lambda:InvokeFunction",
                            "lambda:GetFunctionConfiguration",
                        ],
                        "Resource": BASE_LAMBDA_RESOURCES,
                    },
                    {
                        "Sid": "AWSXRayDaemonWriteAccess",
                        "Effect": "Allow",
                        "Action": [
                            "xray:PutTraceSegments",
                            "xray:PutTelemetryRecords",
                            "xray:GetSamplingRules",
                            "xray:GetSamplingTargets",
                            "xray:GetSamplingStatisticSummaries",
                        ],
                        "Resource": ["*"],
                    },
                ],
            }
        ),
    ),
    opts=pulumi.ResourceOptions(
        parent=query_controller_role,
        depends_on=[
            query_controller_role,
            indexes_table,
        ],
    ),
)

command_controller_role = aws.iam.Role(
    COMMAND_CONTROLLER_ROLE_NAME,
    assume_role_policy=json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Sid": "",
                    "Principal": {"Service": "lambda.amazonaws.com"},
                }
            ],
        }
    ),
    name=COMMAND_CONTROLLER_ROLE_NAME,
)

command_controller_policy = aws.iam.RolePolicy(
    COMMAND_CONTROLLER_POLICY_NAME,
    name=COMMAND_CONTROLLER_POLICY_NAME,
    role=command_controller_role.id,
    policy=pulumi.Output.all(
        PROJECT_TABLE_ARN=projects_table.arn,
        INDEX_TABLE_ARN=indexes_table.arn,
        DELETED_INDEX_TABLE_ARN=deleted_indexes_table.arn,
        S3_TEMP_BUCKET_ARN=s3_temp_bucket.arn,
        S3_BUCKET_ARN=s3_bucket.arn,
    ).apply(
        lambda args: json.dumps(
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "FullAccessForProjectsTable",
                        "Effect": "Allow",
                        "Action": [
                            "dynamodb:BatchGetItem",
                            "dynamodb:BatchWriteItem",
                            "dynamodb:ConditionCheckItem",
                            "dynamodb:PutItem",
                            "dynamodb:DescribeTable",
                            "dynamodb:DeleteItem",
                            "dynamodb:GetItem",
                            "dynamodb:Scan",
                            "dynamodb:Query",
                            "dynamodb:UpdateItem",
                            "dynamodb:GetRecords",
                        ],
                        "Resource": [
                            f"{args['PROJECT_TABLE_ARN']}",
                            f"{args['PROJECT_TABLE_ARN']}/stream/*",
                            f"{args['PROJECT_TABLE_ARN']}/index/*",
                        ],
                    },
                    {
                        "Sid": "FullAccessForIndexesTable",
                        "Effect": "Allow",
                        "Action": [
                            "dynamodb:BatchGetItem",
                            "dynamodb:BatchWriteItem",
                            "dynamodb:ConditionCheckItem",
                            "dynamodb:PutItem",
                            "dynamodb:DescribeTable",
                            "dynamodb:DeleteItem",
                            "dynamodb:GetItem",
                            "dynamodb:Scan",
                            "dynamodb:Query",
                            "dynamodb:UpdateItem",
                            "dynamodb:GetRecords",
                        ],
                        "Resource": [
                            f"{args['INDEX_TABLE_ARN']}",
                            f"{args['INDEX_TABLE_ARN']}/stream/*",
                            f"{args['INDEX_TABLE_ARN']}/index/*",
                        ],
                    },
                    {
                        "Sid": "FullAccessForDeletedIndexesTable",
                        "Effect": "Allow",
                        "Action": [
                            "dynamodb:BatchGetItem",
                            "dynamodb:BatchWriteItem",
                            "dynamodb:ConditionCheckItem",
                            "dynamodb:PutItem",
                            "dynamodb:DescribeTable",
                            "dynamodb:DeleteItem",
                            "dynamodb:GetItem",
                            "dynamodb:Scan",
                            "dynamodb:Query",
                            "dynamodb:UpdateItem",
                            "dynamodb:GetRecords",
                        ],
                        "Resource": [
                            f"{args['DELETED_INDEX_TABLE_ARN']}",
                            f"{args['DELETED_INDEX_TABLE_ARN']}/stream/*",
                            f"{args['DELETED_INDEX_TABLE_ARN']}/index/*",
                        ],
                    },
                    {
                        "Effect": "Allow",
                        "Action": [
                            "dynamodb:ListTables",
                            "dynamodb:DescribeLimits",
                            "dynamodb:Scan",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AccessToSQSControlQueue",
                        "Effect": "Allow",
                        "Action": [
                            "sqs:SendMessage",
                            "sqs:ReceiveMessage",
                            "sqs:DeleteMessage",
                            "sqs:GetQueueAttributes",
                        ],
                        "Resource": BASE_QUEUE_RESOURCES,
                    },
                    {
                        "Sid": "AllowInvokeLambdaFunction",
                        "Effect": "Allow",
                        "Action": [
                            "lambda:InvokeFunction",
                            "lambda:GetFunctionConfiguration",
                        ],
                        "Resource": BASE_LAMBDA_RESOURCES,
                    },
                    {
                        "Sid": "AccessToS3Bucket",
                        "Effect": "Allow",
                        "Action": [
                            "s3:ListBucket*",
                            "s3:GetObject*",
                            "s3:PutObject*",
                            "s3:DeleteObject*",
                        ],
                        "Resource": [
                            f"{args['S3_BUCKET_ARN']}",
                            f"{args['S3_BUCKET_ARN']}/*",
                            f"{args['S3_TEMP_BUCKET_ARN']}",
                            f"{args['S3_TEMP_BUCKET_ARN']}/*",
                        ],
                    },
                    {
                        "Sid": "AWSLambdaVPCAccessExecutionRole",
                        "Effect": "Allow",
                        "Action": [
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents",
                            "ec2:CreateNetworkInterface",
                            "ec2:DescribeNetworkInterfaces",
                            "ec2:DeleteNetworkInterface",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Effect": "Allow",
                        "Action": [
                            "batch:*",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AmazonElasticFileSystemClientFullAccess",
                        "Effect": "Allow",
                        "Action": [
                            "elasticfilesystem:ClientMount",
                            "elasticfilesystem:ClientWrite",
                            "elasticfilesystem:ClientRootAccess",
                            "elasticfilesystem:DescribeMountTargets",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AWSApiGatewayAccess",
                        "Effect": "Allow",
                        "Action": [
                            "apigateway:GET",
                            "apigateway:POST",
                            "apigateway:PUT",
                            "apigateway:DELETE",
                            "apigateway:PATCH",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AWSXRayDaemonWriteAccess",
                        "Effect": "Allow",
                        "Action": [
                            "xray:PutTraceSegments",
                            "xray:PutTelemetryRecords",
                            "xray:GetSamplingRules",
                            "xray:GetSamplingTargets",
                            "xray:GetSamplingStatisticSummaries",
                        ],
                        "Resource": ["*"],
                    },
                ],
            }
        ),
    ),
    opts=pulumi.ResourceOptions(
        parent=command_controller_role,
        depends_on=[
            command_controller_role,
            projects_table,
            indexes_table,
            s3_bucket,
            s3_temp_bucket,
        ],
    ),
)

query_executor_role = aws.iam.Role(
    QUERY_EXECUTOR_ROLE_NAME,
    assume_role_policy=json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Sid": "",
                    "Principal": {"Service": "lambda.amazonaws.com"},
                }
            ],
        }
    ),
    name=QUERY_EXECUTOR_ROLE_NAME,
)

query_executor_policy = aws.iam.RolePolicy(
    QUERY_EXECUTOR_POLICY_NAME,
    name=QUERY_EXECUTOR_POLICY_NAME,
    role=query_executor_role.id,
    policy=pulumi.Output.all(
        PROJECT_TABLE_ARN=projects_table.arn,
        INDEX_TABLE_ARN=indexes_table.arn,
        S3_BUCKET_ARN=s3_bucket.arn,
    ).apply(
        lambda args: json.dumps(
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "ReadOnlyForIndexesTable",
                        "Effect": "Allow",
                        "Action": [
                            "dynamodb:BatchGetItem",
                            "dynamodb:ConditionCheckItem",
                            "dynamodb:DescribeTable",
                            "dynamodb:GetItem",
                            "dynamodb:Scan",
                            "dynamodb:Query",
                            "dynamodb:GetRecords",
                        ],
                        "Resource": [
                            f"{args['INDEX_TABLE_ARN']}",
                            f"{args['INDEX_TABLE_ARN']}/stream/*",
                            f"{args['INDEX_TABLE_ARN']}/index/*",
                        ],
                    },
                    {
                        "Effect": "Allow",
                        "Action": ["dynamodb:ListTables", "dynamodb:DescribeLimits"],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AWSLambdaVPCAccessExecutionRole",
                        "Effect": "Allow",
                        "Action": [
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents",
                            "ec2:CreateNetworkInterface",
                            "ec2:DescribeNetworkInterfaces",
                            "ec2:DeleteNetworkInterface",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AccessToS3Bucket",
                        "Effect": "Allow",
                        "Action": [
                            "s3:ListBucket*",
                            "s3:GetObject*",
                            "s3:PutObject*",
                            "s3:DeleteObject*",
                        ],
                        "Resource": [
                            f"{args['S3_BUCKET_ARN']}",
                            f"{args['S3_BUCKET_ARN']}/*",
                        ],
                    },
                    {
                        "Sid": "AWSXRayDaemonWriteAccess",
                        "Effect": "Allow",
                        "Action": [
                            "xray:PutTraceSegments",
                            "xray:PutTelemetryRecords",
                            "xray:GetSamplingRules",
                            "xray:GetSamplingTargets",
                            "xray:GetSamplingStatisticSummaries",
                        ],
                        "Resource": ["*"],
                    },
                ],
            }
        ),
    ),
    opts=pulumi.ResourceOptions(
        parent=query_executor_role,
        depends_on=[
            query_executor_role,
            projects_table,
            indexes_table,
            s3_bucket,
        ],
    ),
)

index_builder_role = aws.iam.Role(
    INDEX_BUILDER_ROLE_NAME,
    assume_role_policy=json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Principal": {"Service": "batch.amazonaws.com"},
                },
                {
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Principal": {"Service": "ec2.amazonaws.com"},
                },
                {
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Principal": {"Service": "ecs-tasks.amazonaws.com"},
                },
            ],
        }
    ),
    name=INDEX_BUILDER_ROLE_NAME,
)

index_builder_policy = aws.iam.RolePolicy(
    INDEX_BUILDER_POLICY_NAME,
    name=INDEX_BUILDER_POLICY_NAME,
    role=index_builder_role.id,
    policy=pulumi.Output.all(
        PROJECT_TABLE_ARN=projects_table.arn,
        INDEX_TABLE_ARN=indexes_table.arn,
        S3_BUCKET_ARN=s3_bucket.arn,
        S3_TEMP_BUCKET_ARN=s3_temp_bucket.arn,
    ).apply(
        lambda args: json.dumps(
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "fullAccessForIndexesTable",
                        "Effect": "Allow",
                        "Action": [
                            "dynamodb:BatchGetItem",
                            "dynamodb:BatchWriteItem",
                            "dynamodb:ConditionCheckItem",
                            "dynamodb:PutItem",
                            "dynamodb:DescribeTable",
                            "dynamodb:DeleteItem",
                            "dynamodb:GetItem",
                            "dynamodb:Scan",
                            "dynamodb:Query",
                            "dynamodb:UpdateItem",
                            "dynamodb:GetRecords",
                        ],
                        "Resource": [
                            f"{args['INDEX_TABLE_ARN']}",
                            f"{args['INDEX_TABLE_ARN']}/stream/*",
                            f"{args['INDEX_TABLE_ARN']}/index/*",
                        ],
                    },
                    {
                        "Effect": "Allow",
                        "Action": ["dynamodb:ListTables", "dynamodb:DescribeLimits"],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AWSLambdaVPCAccessExecutionRole",
                        "Effect": "Allow",
                        "Action": [
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents",
                            "ec2:CreateNetworkInterface",
                            "ec2:DescribeNetworkInterfaces",
                            "ec2:DeleteNetworkInterface",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AccessToS3Bucket",
                        "Effect": "Allow",
                        "Action": [
                            "s3:ListBucket*",
                            "s3:GetObject*",
                            "s3:PutObject*",
                            "s3:DeleteObject*",
                        ],
                        "Resource": [
                            f"{args['S3_TEMP_BUCKET_ARN']}",
                            f"{args['S3_TEMP_BUCKET_ARN']}/*",
                            f"{args['S3_BUCKET_ARN']}",
                            f"{args['S3_BUCKET_ARN']}/*",
                        ],
                    },
                    {
                        "Sid": "AccessToEcr",
                        "Effect": "Allow",
                        "Action": [
                            "ecr:GetAuthorizationToken",
                            "ecr:BatchCheckLayerAvailability",
                            "ecr:GetDownloadUrlForLayer",
                            "ecr:BatchGetImage",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Effect": "Allow",
                        "Action": [
                            "batch:*",
                            "cloudwatch:GetMetricStatistics",
                            "ec2:DescribeSubnets",
                            "ec2:DescribeSecurityGroups",
                            "ec2:DescribeKeyPairs",
                            "ec2:DescribeVpcs",
                            "ec2:DescribeImages",
                            "ec2:DescribeLaunchTemplates",
                            "ec2:DescribeLaunchTemplateVersions",
                            "ecs:DescribeClusters",
                            "ecs:DeleteCluster",
                            "ecs:Describe*",
                            "ecs:List*",
                            "eks:DescribeCluster",
                            "eks:ListClusters",
                            "logs:Describe*",
                            "logs:Get*",
                            "logs:TestMetricFilter",
                            "logs:FilterLogEvents",
                            "iam:ListInstanceProfiles",
                            "iam:ListRoles",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Effect": "Allow",
                        "Action": ["iam:PassRole"],
                        "Resource": [
                            "arn:aws:iam::*:role/AWSBatchServiceRole",
                            "arn:aws:iam::*:role/service-role/AWSBatchServiceRole",
                            "arn:aws:iam::*:role/ecsInstanceRole",
                            "arn:aws:iam::*:instance-profile/ecsInstanceRole",
                            "arn:aws:iam::*:role/aws-ec2-spot-fleet-role",
                            "arn:aws:iam::*:role/AWSBatchJobRole*",
                        ],
                    },
                    {
                        "Effect": "Allow",
                        "Action": ["iam:CreateServiceLinkedRole"],
                        "Resource": "arn:aws:iam::*:role/*Batch*",
                        "Condition": {
                            "StringEquals": {
                                "iam:AWSServiceName": "batch.amazonaws.com"
                            }
                        },
                    },
                    {
                        "Sid": "AWSBatchPolicyStatement1",
                        "Effect": "Allow",
                        "Action": [
                            "ec2:DescribeAccountAttributes",
                            "ec2:DescribeInstances",
                            "ec2:DescribeInstanceStatus",
                            "ec2:DescribeInstanceAttribute",
                            "ec2:DescribeSubnets",
                            "ec2:DescribeSecurityGroups",
                            "ec2:DescribeKeyPairs",
                            "ec2:DescribeImages",
                            "ec2:DescribeImageAttribute",
                            "ec2:DescribeSpotInstanceRequests",
                            "ec2:DescribeSpotFleetInstances",
                            "ec2:DescribeSpotFleetRequests",
                            "ec2:DescribeSpotPriceHistory",
                            "ec2:DescribeSpotFleetRequestHistory",
                            "ec2:DescribeVpcClassicLink",
                            "ec2:DescribeLaunchTemplateVersions",
                            "ec2:CreateLaunchTemplate",
                            "ec2:DeleteLaunchTemplate",
                            "ec2:RequestSpotFleet",
                            "ec2:CancelSpotFleetRequests",
                            "ec2:ModifySpotFleetRequest",
                            "ec2:TerminateInstances",
                            "ec2:RunInstances",
                            "autoscaling:DescribeAccountLimits",
                            "autoscaling:DescribeAutoScalingGroups",
                            "autoscaling:DescribeLaunchConfigurations",
                            "autoscaling:DescribeAutoScalingInstances",
                            "autoscaling:DescribeScalingActivities",
                            "autoscaling:CreateLaunchConfiguration",
                            "autoscaling:CreateAutoScalingGroup",
                            "autoscaling:UpdateAutoScalingGroup",
                            "autoscaling:SetDesiredCapacity",
                            "autoscaling:DeleteLaunchConfiguration",
                            "autoscaling:DeleteAutoScalingGroup",
                            "autoscaling:CreateOrUpdateTags",
                            "autoscaling:SuspendProcesses",
                            "autoscaling:PutNotificationConfiguration",
                            "autoscaling:TerminateInstanceInAutoScalingGroup",
                            "ecs:DescribeClusters",
                            "ecs:DescribeContainerInstances",
                            "ecs:DescribeTaskDefinition",
                            "ecs:DescribeTasks",
                            "ecs:ListAccountSettings",
                            "ecs:ListClusters",
                            "ecs:ListContainerInstances",
                            "ecs:ListTaskDefinitionFamilies",
                            "ecs:ListTaskDefinitions",
                            "ecs:ListTasks",
                            "ecs:CreateCluster",
                            "ecs:DeleteCluster",
                            "ecs:RegisterTaskDefinition",
                            "ecs:DeregisterTaskDefinition",
                            "ecs:RunTask",
                            "ecs:StartTask",
                            "ecs:StopTask",
                            "ecs:UpdateContainerAgent",
                            "ecs:DeregisterContainerInstance",
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents",
                            "logs:DescribeLogGroups",
                            "iam:GetInstanceProfile",
                            "iam:GetRole",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AWSBatchPolicyStatement2",
                        "Effect": "Allow",
                        "Action": "ecs:TagResource",
                        "Resource": ["arn:aws:ecs:*:*:task/*_Batch_*"],
                    },
                    {
                        "Sid": "AWSBatchPolicyStatement3",
                        "Effect": "Allow",
                        "Action": "iam:PassRole",
                        "Resource": ["*"],
                        "Condition": {
                            "StringEquals": {
                                "iam:PassedToService": [
                                    "ec2.amazonaws.com",
                                    "ec2.amazonaws.com.cn",
                                    "ecs-tasks.amazonaws.com",
                                ]
                            }
                        },
                    },
                    {
                        "Sid": "AWSBatchPolicyStatement4",
                        "Effect": "Allow",
                        "Action": "iam:CreateServiceLinkedRole",
                        "Resource": "*",
                        "Condition": {
                            "StringEquals": {
                                "iam:AWSServiceName": [
                                    "spot.amazonaws.com",
                                    "spotfleet.amazonaws.com",
                                    "autoscaling.amazonaws.com",
                                    "ecs.amazonaws.com",
                                ]
                            }
                        },
                    },
                    {
                        "Sid": "AWSBatchPolicyStatement5",
                        "Effect": "Allow",
                        "Action": ["ec2:CreateTags"],
                        "Resource": ["*"],
                        "Condition": {
                            "StringEquals": {"ec2:CreateAction": "RunInstances"}
                        },
                    },
                    {
                        "Action": [
                            "ssmmessages:CreateControlChannel",
                            "ssmmessages:CreateDataChannel",
                            "ssmmessages:OpenControlChannel",
                            "ssmmessages:OpenDataChannel",
                        ],
                        "Effect": "Allow",
                        "Resource": "*",
                    },
                    {
                        "Sid": "AmazonElasticFileSystemClientFullAccess",
                        "Effect": "Allow",
                        "Action": [
                            "elasticfilesystem:ClientMount",
                            "elasticfilesystem:ClientWrite",
                            "elasticfilesystem:ClientRootAccess",
                            "elasticfilesystem:DescribeMountTargets",
                        ],
                        "Resource": "*",
                    },
                ],
            }
        ),
    ),
    opts=pulumi.ResourceOptions(
        parent=index_builder_role,
        depends_on=[
            index_builder_role,
            projects_table,
            indexes_table,
            s3_bucket,
            s3_temp_bucket,
        ],
    ),
)

index_refresher_role = aws.iam.Role(
    INDEX_REFRESHER_ROLE_NAME,
    assume_role_policy=json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Action": "sts:AssumeRole",
                    "Effect": "Allow",
                    "Sid": "",
                    "Principal": {"Service": "lambda.amazonaws.com"},
                }
            ],
        }
    ),
    name=INDEX_REFRESHER_ROLE_NAME,
)
index_refresher_policy = aws.iam.RolePolicy(
    INDEX_REFRESHER_POLICY_NAME,
    name=INDEX_REFRESHER_POLICY_NAME,
    role=index_refresher_role.id,
    policy=pulumi.Output.all(
        S3_TEMP_BUCKET_ARN=s3_temp_bucket.arn
    ).apply(
        lambda args: json.dumps(
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "AWSLambdaVPCAccessExecutionRole",
                        "Effect": "Allow",
                        "Action": [
                            "logs:CreateLogGroup",
                            "logs:CreateLogStream",
                            "logs:PutLogEvents",
                            "ec2:CreateNetworkInterface",
                            "ec2:DescribeNetworkInterfaces",
                            "ec2:DeleteNetworkInterface",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AccessToS3Bucket",
                        "Effect": "Allow",
                        "Action": [
                            "s3:ListBucket*",
                            "s3:GetObject*",
                            "s3:PutObject*",
                            "s3:DeleteObject*",
                        ],
                        "Resource": [
                            f"{args['S3_TEMP_BUCKET_ARN']}",
                            f"{args['S3_TEMP_BUCKET_ARN']}/*"
                        ],
                    },
                    {
                        "Sid": "AllowInvokeLambdaFunction",
                        "Effect": "Allow",
                        "Action": [
                            "lambda:InvokeFunction",
                            "lambda:GetFunctionConfiguration",
                        ],
                        "Resource": BASE_LAMBDA_RESOURCES,
                    },
                    {
                        "Sid": "AmazonElasticFileSystemClientFullAccess",
                        "Effect": "Allow",
                        "Action": [
                            "elasticfilesystem:ClientMount",
                            "elasticfilesystem:ClientWrite",
                            "elasticfilesystem:ClientRootAccess",
                            "elasticfilesystem:DescribeMountTargets",
                        ],
                        "Resource": "*",
                    },
                    {
                        "Sid": "AWSXRayDaemonWriteAccess",
                        "Effect": "Allow",
                        "Action": [
                            "xray:PutTraceSegments",
                            "xray:PutTelemetryRecords",
                            "xray:GetSamplingRules",
                            "xray:GetSamplingTargets",
                            "xray:GetSamplingStatisticSummaries",
                        ],
                        "Resource": ["*"],
                    },
                ],
            }
        ),
    ),
    opts=pulumi.ResourceOptions(
        parent=index_refresher_role,
        depends_on=[
            index_refresher_role,
            s3_temp_bucket,
            s3_bucket
        ],
    ),
)

# EFS
efs = aws.efs.FileSystem(
    EFS_NAME,
    creation_token=EFS_NAME,
    encrypted=True,
    performance_mode="generalPurpose",
    protection={
        "replication_overwrite": "ENABLED",
    },
    throughput_mode="elastic",
    lifecycle_policies=[
        {"transition_to_ia": "AFTER_7_DAYS"},
        {"transition_to_archive": "AFTER_30_DAYS"},
        {"transition_to_primary_storage_class": "AFTER_1_ACCESS"},
    ],
    tags={
        "Name": EFS_NAME,
    },
)

efs_mount_target0 = aws.efs.MountTarget(
    EFS_MOUNT_TARGET_NAME0,
    file_system_id=efs.id,
    subnet_id=VPC_PRIVATE_SUBNET_ID0,
    security_groups=[VPC_SECURITY_GROUP_ID],
    opts=pulumi.ResourceOptions(
        depends_on=[efs],
    ),
)

efs_mount_target1 = aws.efs.MountTarget(
    EFS_MOUNT_TARGET_NAME1,
    file_system_id=efs.id,
    subnet_id=VPC_PRIVATE_SUBNET_ID1,
    security_groups=[VPC_SECURITY_GROUP_ID],
    opts=pulumi.ResourceOptions(
        depends_on=[efs],
    ),
)

efs_mount_target2 = aws.efs.MountTarget(
    EFS_MOUNT_TARGET_NAME2,
    file_system_id=efs.id,
    subnet_id=VPC_PRIVATE_SUBNET_ID2,
    security_groups=[VPC_SECURITY_GROUP_ID],
    opts=pulumi.ResourceOptions(
        depends_on=[efs],
    ),
)

efs_access_point = aws.efs.AccessPoint(
    EFS_ACCESS_POINT_NAME,
    file_system_id=efs.id,
    posix_user={
        "gid": 1000,
        "uid": 1000,
    },
    root_directory={
        "path": EFS_ROOT_PATH,
        "creation_info": {
            "owner_uid": 1000,
            "owner_gid": 1000,
            "permissions": 755,
        },
    },
    tags={
        "Name": EFS_ACCESS_POINT_NAME,
    },
    opts=pulumi.ResourceOptions(
        depends_on=[efs, efs_mount_target0, efs_mount_target1, efs_mount_target2],
    ),
)

# Upload the Lambda layer zip to the S3 bucket
function_object = aws.s3.BucketObject(
    FUNCTION_OBJECT_NAME,
    bucket=s3_bucket.id,
    source=pulumi.FileAsset(FUNCTION_LOCAL_PATH),
    key=FUNCTION_S3_KEY,
    source_hash=std.filebase64sha256(FUNCTION_LOCAL_PATH).result,
)

# Lambda Functions
command_controller_log_group = aws.cloudwatch.LogGroup(
    COMMAND_CONTROLLER_LOG_GROUP_NAME,
    name=f"/aws/lambda/{COMMAND_CONTROLLER_NAME}",
    retention_in_days=LOG_RETENTION_IN_DAYS,
)

command_controller = aws.lambda_.Function(
    COMMAND_CONTROLLER_NAME,
    name=COMMAND_CONTROLLER_NAME,
    role=command_controller_role.arn,
    s3_bucket=s3_bucket.id,
    s3_key=function_object.key,
    handler="io.wrtn.lambda.CommandController::handleRequest",
    source_code_hash=function_object.source_hash,
    runtime=LAMBDA_RUNTIME_CONFIG,
    architectures=[LAMBDA_ARM_ARCHITECTURE],
    memory_size=DEFAULT_LAMBDA_MEMORY_SIZE_MB,
    ephemeral_storage=DEFAULT_STORAGE_CONFIG,
    timeout=COMMAND_CONTROLLER_TIMEOUT,
    environment=get_lambda_environment_config(COMMAND_CONTROLLER_QUALIFIER),
    vpc_config=aws.lambda_.FunctionVpcConfigArgs(
        security_group_ids=[VPC_SECURITY_GROUP_ID],
        subnet_ids=[
            VPC_PRIVATE_SUBNET_ID0,
            VPC_PRIVATE_SUBNET_ID1,
            VPC_PRIVATE_SUBNET_ID2,
        ],
    ),
    logging_config=LOG_CONFIG,
    publish=True,
    file_system_config={
        "arn": efs_access_point.arn,
        "localMountPath": EFS_MOUNT_PATH,
    },
    opts=pulumi.ResourceOptions(
        depends_on=[
            command_controller_log_group,
            control_queue,
            command_controller_role,
            efs_access_point,
            function_object
        ],
    ),
)

command_controller_alias = aws.lambda_.Alias(
    COMMAND_CONTROLLER_ALIAS_NAME,
    function_name=command_controller.name,
    function_version=command_controller.version,
    name=COMMAND_CONTROLLER_QUALIFIER,
    opts=pulumi.ResourceOptions(
        depends_on=[command_controller],
    ),
)

index_refresher_log_group = aws.cloudwatch.LogGroup(
    INDEX_REFRESHER_LOG_GROUP_NAME,
    name=f"/aws/lambda/{INDEX_REFRESHER_NAME}",
    retention_in_days=LOG_RETENTION_IN_DAYS,
)

index_refresher = aws.lambda_.Function(
    f"{INDEX_REFRESHER_NAME}",
    name=f"{INDEX_REFRESHER_NAME}",
    s3_bucket=s3_bucket.id,
    s3_key=function_object.key,
    role=index_refresher_role.arn,
    handler="io.wrtn.lambda.IndexRefresher::handleRequest",
    source_code_hash=function_object.source_hash,
    runtime=LAMBDA_RUNTIME_CONFIG,
    architectures=[LAMBDA_ARM_ARCHITECTURE],
    memory_size=DEFAULT_LAMBDA_MEMORY_SIZE_MB,
    ephemeral_storage=DEFAULT_STORAGE_CONFIG,
    timeout=DEFAULT_LAMBDA_TIMEOUT,
    environment=get_lambda_environment_config(INDEX_REFRESHER_QUALIFIER),
    vpc_config=aws.lambda_.FunctionVpcConfigArgs(
        security_group_ids=[VPC_SECURITY_GROUP_ID],
        subnet_ids=[
            VPC_PRIVATE_SUBNET_ID0,
            VPC_PRIVATE_SUBNET_ID1,
            VPC_PRIVATE_SUBNET_ID2,
        ],
    ),
    logging_config=LOG_CONFIG,
    publish=True,
    file_system_config={
        "arn": efs_access_point.arn,
        "localMountPath": EFS_MOUNT_PATH,
    },
    opts=pulumi.ResourceOptions(
        depends_on=[
            index_refresher_log_group,
            index_refresher_role,
            efs_access_point,
            function_object,
        ],
    ),
)

index_refresher_alias = aws.lambda_.Alias(
    INDEX_REFRESHER_ALIAS_NAME,
    function_name=index_refresher.name,
    function_version=index_refresher.version,
    name=INDEX_REFRESHER_QUALIFIER,
    opts=pulumi.ResourceOptions(
        depends_on=[index_refresher],
    ),
)

for identifier in range(MAX_NUM_COMPUTE_NODES_STD):
    query_executor(identifier, "std")

for identifier in range(MAX_NUM_COMPUTE_NODES_IA):
    query_executor(identifier, "ia")

request_controller_log_group = aws.cloudwatch.LogGroup(
    REQUEST_CONTROLLER_LOG_GROUP_NAME,
    name=f"/aws/lambda/{REQUEST_CONTROLLER_NAME}",
    retention_in_days=LOG_RETENTION_IN_DAYS,
)

request_controller = aws.lambda_.Function(
    f"{REQUEST_CONTROLLER_NAME}",
    name=f"{REQUEST_CONTROLLER_NAME}",
    s3_bucket=s3_bucket.id,
    s3_key=function_object.key,
    role=request_controller_role.arn,
    handler="io.wrtn.lambda.RequestController::handleRequest",
    source_code_hash=function_object.source_hash,
    runtime=LAMBDA_RUNTIME_CONFIG,
    architectures=[LAMBDA_ARM_ARCHITECTURE],
    memory_size=DEFAULT_LAMBDA_MEMORY_SIZE_MB,
    ephemeral_storage=DEFAULT_STORAGE_CONFIG,
    timeout=DEFAULT_LAMBDA_TIMEOUT,
    environment=get_lambda_environment_config(REQUEST_CONTROLLER_QUALIFIER),
    vpc_config=aws.lambda_.FunctionVpcConfigArgs(
        security_group_ids=[VPC_SECURITY_GROUP_ID],
        subnet_ids=[
            VPC_PRIVATE_SUBNET_ID0,
            VPC_PRIVATE_SUBNET_ID1,
            VPC_PRIVATE_SUBNET_ID2,
        ],
    ),
    logging_config=LOG_CONFIG,
    publish=True,
    file_system_config={
        "arn": efs_access_point.arn,
        "localMountPath": EFS_MOUNT_PATH,
    },
    opts=pulumi.ResourceOptions(
        depends_on=[
            request_controller_log_group,
            request_controller_role,
            function_object
        ],
    ),
)

request_controller_alias = aws.lambda_.Alias(
    REQUEST_CONTROLLER_ALIAS_NAME,
    function_name=request_controller.name,
    function_version=request_controller.version,
    name=REQUEST_CONTROLLER_QUALIFIER,
    opts=pulumi.ResourceOptions(
        depends_on=[request_controller],
    ),
)

query_controller_log_group = aws.cloudwatch.LogGroup(
    QUERY_CONTROLLER_LOG_GROUP_NAME,
    name=f"/aws/lambda/{QUERY_CONTROLLER_NAME}",
    retention_in_days=LOG_RETENTION_IN_DAYS,
)

query_controller = aws.lambda_.Function(
    f"{QUERY_CONTROLLER_NAME}",
    name=f"{QUERY_CONTROLLER_NAME}",
    s3_bucket=s3_bucket.id,
    s3_key=function_object.key,
    role=query_controller_role.arn,
    handler="io.wrtn.lambda.QueryController::handleRequest",
    source_code_hash=function_object.source_hash,
    runtime=LAMBDA_RUNTIME_CONFIG,
    architectures=[LAMBDA_ARM_ARCHITECTURE],
    memory_size=DEFAULT_LAMBDA_MEMORY_SIZE_MB,
    ephemeral_storage=DEFAULT_STORAGE_CONFIG,
    timeout=DEFAULT_LAMBDA_TIMEOUT,
    environment=get_lambda_environment_config(QUERY_CONTROLLER_QUALIFIER),
    logging_config=LOG_CONFIG,
    publish=True,
    opts=pulumi.ResourceOptions(
        depends_on=[
            query_controller_log_group,
            query_controller_role,
            function_object
        ],
    ),
)

query_controller_alias = aws.lambda_.Alias(
    QUERY_CONTROLLER_ALIAS_NAME,
    function_name=query_controller.name,
    function_version=query_controller.version,
    name=QUERY_CONTROLLER_QUALIFIER,
    opts=pulumi.ResourceOptions(
        depends_on=[query_controller],
    ),
)

# Event Source Mapping for command processing
lambda_event_source_mapping = aws.lambda_.EventSourceMapping(
    CONTROL_QUEUE_EVENT_SOURCE_MAPPING_NAME,
    event_source_arn=control_queue.arn,
    function_name=command_controller_alias.arn,
    opts=pulumi.ResourceOptions(
        depends_on=[
            command_controller,
            command_controller_alias,
            control_queue,
        ]
    ),
)

# EventBridge Scheduler
schedule_group = aws.scheduler.ScheduleGroup(
    SCHEDULE_GROUP_NAME, name=SCHEDULE_GROUP_NAME
)

index_build_schedule = aws.scheduler.Schedule(
    INDEX_BUILD_SCHEDULE_NAME,
    name=INDEX_BUILD_SCHEDULE_NAME,
    group_name=schedule_group.name,
    flexible_time_window=FLEXIBLE_TIME_WINDOW_OFF_MODE,
    state="ENABLED",
    schedule_expression=DEFAULT_SCHEDULE_EXPRESSION,
    target=aws.scheduler.ScheduleTargetArgs(
        arn=control_queue.arn,
        role_arn=scheduler_role.arn,
        retry_policy=aws.scheduler.ScheduleTargetRetryPolicyArgs(
            maximum_event_age_in_seconds=SCHEDULER_MAXIMUM_EVENT_AGE_IN_SECONDS,
            maximum_retry_attempts=SCHEDULER_MAXIMUM_RETRY_ATTEMPTS,
        ),
        input='{"type": "INDEX_BUILD"}',
    ),
    opts=pulumi.ResourceOptions(
        depends_on=[
            schedule_group,
            scheduler_role,
            control_queue,
            command_controller,
            command_controller_alias,
        ]
    ),
)

index_resched_schedule = aws.scheduler.Schedule(
    INDEX_RESCHED_SCHEDULE_NAME,
    name=INDEX_RESCHED_SCHEDULE_NAME,
    group_name=schedule_group.name,
    flexible_time_window=FLEXIBLE_TIME_WINDOW_OFF_MODE,
    state="ENABLED",
    schedule_expression=DEFAULT_SCHEDULE_EXPRESSION,
    target=aws.scheduler.ScheduleTargetArgs(
        arn=control_queue.arn,
        role_arn=scheduler_role.arn,
        retry_policy=aws.scheduler.ScheduleTargetRetryPolicyArgs(
            maximum_event_age_in_seconds=SCHEDULER_MAXIMUM_EVENT_AGE_IN_SECONDS,
            maximum_retry_attempts=SCHEDULER_MAXIMUM_RETRY_ATTEMPTS,
        ),
        input='{"type": "INDEX_RESCHED"}',
    ),
    opts=pulumi.ResourceOptions(
        depends_on=[
            schedule_group,
            control_queue,
            scheduler_role,
            command_controller,
            command_controller_alias,
        ]
    ),
)

# Batch
index_builder_ecr_repo = aws.ecr.Repository(
    INDEX_BUILDER_ECR_REPO_NAME,
    name=INDEX_BUILDER_ECR_REPO_NAME,
    image_tag_mutability="MUTABLE",
    force_delete=True,
    opts=pulumi.ResourceOptions(
        parent=index_builder_role,
        depends_on=[index_builder_role],
    ),
)

index_builder_lifecycle_policy = aws.ecr.LifecyclePolicy(
    INDEX_BUILDER_ECR_LIFE_CYCLE_POLICY_NAME,
    repository=index_builder_ecr_repo.name,
    policy="""{
            "rules": [
                {
                    "rulePriority": 1,
                    "description": "Expire images older than 7 days",
                    "selection": {
                        "tagStatus": "untagged",
                        "countType": "sinceImagePushed",
                        "countUnit": "days",
                        "countNumber": 7
                    },
                    "action": {
                        "type": "expire"
                    }
                }
            ]
        }
""",
    opts=pulumi.ResourceOptions(
        depends_on=[index_builder_ecr_repo],
    ),
)

ecr_creds = index_builder_ecr_repo.registry_id.apply(
    lambda id: aws.ecr.get_authorization_token(registry_id=id)
)

auth_token = aws.ecr.get_authorization_token_output(
    registry_id=index_builder_ecr_repo.registry_id
)

amd64_image = docker_build.Image(
    f"{INDEX_BUILDER_ECR_IMAGE_NAME}-amd64",
    tags=[
        index_builder_ecr_repo.repository_url.apply(
            lambda repository_url: f"{repository_url}:cache-amd64"
        )
    ],
    context=docker_build.BuildContextArgs(
        location=INDEX_BUILDER_LOCAL_PATH,
    ),
    dockerfile=docker_build.DockerfileArgs(location=INDEX_BUILDER_DOCKER_FILE_PATH),
    cache_from=[
        docker_build.CacheFromArgs(
            registry=docker_build.CacheFromRegistryArgs(
                ref=index_builder_ecr_repo.repository_url.apply(
                    lambda repository_url: f"{repository_url}:cache-amd64"
                ),
            ),
        )
    ],
    cache_to=[
        docker_build.CacheToArgs(
            inline=docker_build.CacheToInlineArgs(),
        )
    ],
    platforms=[
        docker_build.Platform.LINUX_AMD64,
    ],
    push=True,
    registries=[
        docker_build.RegistryArgs(
            address=index_builder_ecr_repo.repository_url,
            password=auth_token.password,
            username=auth_token.user_name,
        )
    ],
    opts=pulumi.ResourceOptions(
        parent=index_builder_ecr_repo,
        depends_on=[index_builder_ecr_repo],
    ),
)

arm64_image = docker_build.Image(
    f"{INDEX_BUILDER_ECR_IMAGE_NAME}-arm64",
    tags=[
        index_builder_ecr_repo.repository_url.apply(
            lambda repository_url: f"{repository_url}:cache-arm64"
        )
    ],
    context=docker_build.BuildContextArgs(
        location=INDEX_BUILDER_LOCAL_PATH,
    ),
    dockerfile=docker_build.DockerfileArgs(location=INDEX_BUILDER_DOCKER_FILE_PATH),
    cache_from=[
        docker_build.CacheFromArgs(
            registry=docker_build.CacheFromRegistryArgs(
                ref=index_builder_ecr_repo.repository_url.apply(
                    lambda repository_url: f"{repository_url}:cache-arm64"
                ),
            ),
        )
    ],
    cache_to=[
        docker_build.CacheToArgs(
            inline=docker_build.CacheToInlineArgs(),
        )
    ],
    platforms=[
        docker_build.Platform.LINUX_ARM64,
    ],
    push=True,
    registries=[
        docker_build.RegistryArgs(
            address=index_builder_ecr_repo.repository_url,
            password=auth_token.password,
            username=auth_token.user_name,
        )
    ],
    opts=pulumi.ResourceOptions(
        parent=index_builder_ecr_repo,
        depends_on=[index_builder_ecr_repo],
    ),
)

index = docker_build.Index(
    INDEX_BUILDER_ECR_IMAGE_NAME,
    sources=[
        amd64_image.ref,
        arm64_image.ref,
    ],
    tag=index_builder_ecr_repo.repository_url.apply(
        lambda repository_url: f"{repository_url}:{ENV}"
    ),
    registry=docker_build.RegistryArgs(
        address=index_builder_ecr_repo.repository_url,
        password=auth_token.password,
        username=auth_token.user_name,
    ),
    opts=pulumi.ResourceOptions(
        depends_on=[amd64_image, arm64_image], replace_on_changes=["sources"]
    ),
)

index_builder_log_group = aws.cloudwatch.LogGroup(
    INDEX_BUILDER_LOG_GROUP_NAME,
    name=f"/aws/batch/{INDEX_BUILDER_NAME}",
    retention_in_days=LOG_RETENTION_IN_DAYS,
    opts=pulumi.ResourceOptions(
        parent=index_builder_policy,
        depends_on=[index_builder_role, index_builder_policy],
    ),
)

compute_environment = aws.batch.ComputeEnvironment(
    INDEX_BUILDER_COMPUTE_ENVIRONMENT_NAME,
    compute_environment_name=INDEX_BUILDER_COMPUTE_ENVIRONMENT_NAME,
    type="MANAGED",
    service_role=index_builder_role.arn,
    state="ENABLED",
    compute_resources=aws.batch.ComputeEnvironmentComputeResourcesArgs(
        max_vcpus=INDEX_BUILDER_MAX_COMPUTE_VCPUS,
        security_group_ids=[VPC_SECURITY_GROUP_ID],
        subnets=[
            VPC_PRIVATE_SUBNET_ID0,
            VPC_PRIVATE_SUBNET_ID1,
            VPC_PRIVATE_SUBNET_ID2,
        ],
        type="FARGATE",
    ),
    opts=pulumi.ResourceOptions(
        parent=index_builder_policy,
        depends_on=[index_builder_policy, index_builder_role],
    ),
)

index_builder_job_queue = aws.batch.JobQueue(
    INDEX_BUILDER_JOB_QUEUE_NAME,
    name=INDEX_BUILDER_JOB_QUEUE_NAME,
    state="ENABLED",
    priority=1000,
    compute_environment_orders=[
        aws.batch.JobQueueComputeEnvironmentOrderArgs(
            order=1,
            compute_environment=compute_environment.arn,
        ),
    ],
    opts=pulumi.ResourceOptions(
        parent=index_builder_policy,
        depends_on=[
            index_builder_policy,
            index_builder_role,
            compute_environment,
        ],
    ),
)

index_builder_job_definition = aws.batch.JobDefinition(
    INDEX_BUILDER_JOB_DEFINITION_NAME,
    name=INDEX_BUILDER_JOB_DEFINITION_NAME,
    type="container",
    container_properties=make_container_properties(),
    platform_capabilities=["FARGATE"],
    retry_strategy={"attempts": INDEX_BUILDER_RETRY_ATTEMPTS},
    timeout={"attempt_duration_seconds": INDEX_BUILDER_TIMEOUT},
    propagate_tags=True,
    opts=pulumi.ResourceOptions(
        parent=index_builder_policy,
        depends_on=[
            index_builder_policy,
            index_builder_role,
            index_builder_ecr_repo,
            index_builder_log_group,
            efs,
            efs_access_point,
        ],
    ),
)

# Create API Gateway log group
api_gateway_log_group = aws.cloudwatch.LogGroup(
    API_GATEWAY_LOG_GROUP_NAME,
    name=f"/aws/apiGateWay/{API_GATEWAY_NAME}",
    retention_in_days=LOG_RETENTION_IN_DAYS,
)

# Create API routes
routes_settings = []
for route in ROUTES:
    routes_settings.append(
        apigateway.RouteArgs(
            path=route["path"],
            method=route["method"],
            event_handler=request_controller,
            content_type="application/json",
            api_key_required=True,
        )
    )

for route in QUERY_ROUTES:
    routes_settings.append(
        apigateway.RouteArgs(
            path=route["path"],
            method=route["method"],
            event_handler=query_controller,
            content_type="application/json",
            api_key_required=True,
        )
    )

if ENV == "prod":
    # Create API GW VPC endpoints
    api_gw_vpc_endpoint = aws.ec2.VpcEndpoint(
        API_GATEWAY_ENDPOINT_NAME,
        vpc_id=VPC_ID,
        service_name=f"com.amazonaws.{AWS_REGION}.execute-api",
        vpc_endpoint_type="Interface",
        subnet_ids=[
            VPC_PRIVATE_SUBNET_ID0,
            VPC_PRIVATE_SUBNET_ID1,
            VPC_PRIVATE_SUBNET_ID2
        ],
        security_group_ids=[VPC_SECURITY_GROUP_ID],
        private_dns_enabled=False,
        tags={
            "Name": API_GATEWAY_ENDPOINT_NAME,
        },
    )

    # Create private API GW
    try:
        get_private_api = aws.apigateway.get_rest_api(name=API_GATEWAY_NAME_PRIVATE)
        private_api_resource_arn = f"arn:aws:execute-api:{AWS_REGION}:{AWS_ACCOUNT_ID}:{get_private_api.id}/*/*/*"
    except:
        private_api_resource_arn = "execute-api/*/*/*"

    def rest_api_transform_private(args):
        if args.type_ == "aws:apigateway/restApi:RestApi":
            args.props["fail_on_warnings"] = True
            args.props["endpoint_configuration"] = {
                "types": "PRIVATE",
                "vpc_endpoint_ids": [
                    api_gw_vpc_endpoint.id,
                ]
            }
            args.props["policy"] = json.dumps({
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Principal": "*",
                        "Action": "execute-api:Invoke",
                        "Resource": private_api_resource_arn
                    }
                ]
            })

            return pulumi.ResourceTransformResult(args.props, args.opts)

    api_private = apigateway.RestAPI(
        API_GATEWAY_NAME_PRIVATE,
        api_key_source=apigateway.APIKeySource.HEADER,
        routes=routes_settings,
        stage_name=API_VERSION,
        binary_media_types=[],
        opts=pulumi.ResourceOptions(
            depends_on=[
                request_controller,
                query_controller,
                api_gateway_log_group,
                api_gw_vpc_endpoint
            ],
            transforms=[rest_api_transform_private],
        ),
    )

    aws.lambda_.Permission(
        "api-private-http-request-lambda-permission",
        action="lambda:invokeFunction",
        function=request_controller.name,
        principal="apigateway.amazonaws.com",
        source_arn=api_private.api.execution_arn.apply(lambda arn: arn + "*/*"),
        opts=pulumi.ResourceOptions(
            depends_on=[
                api_private,
                request_controller,
            ]
        ),
    )

    aws.lambda_.Permission(
        "api-private-http-query-lambda-permission",
        action="lambda:invokeFunction",
        function=query_controller.name,
        principal="apigateway.amazonaws.com",
        source_arn=api_private.api.execution_arn.apply(lambda arn: arn + "*/*"),
        opts=pulumi.ResourceOptions(
            depends_on=[
                api_private,
                query_controller,
            ]
        ),
    )

# Create API Gateway
def rest_api_transform(args):
    if args.type_ == "aws:apigateway/restApi:RestApi":
        args.props["fail_on_warnings"] = True
        args.props["endpoint_configuration"] = {
            "types": "REGIONAL"
        }

        return pulumi.ResourceTransformResult(args.props, args.opts)

api = apigateway.RestAPI(
    API_GATEWAY_NAME,
    api_key_source=apigateway.APIKeySource.HEADER,
    routes=routes_settings,
    stage_name=API_VERSION,
    binary_media_types=[],
    opts=pulumi.ResourceOptions(
        depends_on=[
            request_controller,
            query_controller,
            api_gateway_log_group
        ],
        transforms=[rest_api_transform],
    ),
)

# Create admin usage plan
admin_usage_plan = aws.apigateway.UsagePlan(
    ADMIN_USAGE_PLAN_NAME,
    api_stages=[
                   {
                       "api_id": api.api.id,
                       "stage": api.stage.stage_name,
                   }
               ]
               + (
                   [
                       {
                           "api_id": api_private.api.id,
                           "stage": api_private.stage.stage_name,
                       }
                   ]
                   if ENV == "prod"
                   else []
               ),
    name=ADMIN_USAGE_PLAN_NAME,
    throttle_settings={
        "burst_limit": 10,
        "rate_limit": 10,
    },
    opts=pulumi.ResourceOptions(
        depends_on=[api, api_private] if ENV == "prod" else [api]
    ),
)

admin_usage_plan_key = aws.apigateway.UsagePlanKey(
    ADMIN_USAGE_PLAN_KEY_NAME,
    key_id=admin_api_key.id,
    key_type="API_KEY",
    usage_plan_id=admin_usage_plan.id,
    opts=pulumi.ResourceOptions(depends_on=[admin_api_key, admin_usage_plan]),
)

# Create API Gateway Lambda Permissions
aws.lambda_.Permission(
    "api-http-request-lambda-permission",
    action="lambda:invokeFunction",
    function=request_controller.name,
    principal="apigateway.amazonaws.com",
    source_arn=api.api.execution_arn.apply(lambda arn: arn + "*/*"),
    opts=pulumi.ResourceOptions(
        depends_on=[
            api,
            request_controller,
        ]
    ),
)

aws.lambda_.Permission(
    "api-http-query-lambda-permission",
    action="lambda:invokeFunction",
    function=query_controller.name,
    principal="apigateway.amazonaws.com",
    source_arn=api.api.execution_arn.apply(lambda arn: arn + "*/*"),
    opts=pulumi.ResourceOptions(
        depends_on=[
            api,
            query_controller,
        ]
    ),
)
