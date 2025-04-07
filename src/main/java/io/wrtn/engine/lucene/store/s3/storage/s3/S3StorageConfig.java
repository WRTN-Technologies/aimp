package io.wrtn.engine.lucene.store.s3.storage.s3;

import io.wrtn.infra.aws.S3;

/**
 * A S3 storage configuration.
 */
public record S3StorageConfig(String bucket, S3 s3) {}
