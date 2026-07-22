package com.ojimasam.safy.media.application;

import java.time.Duration;
import java.time.Instant;

public interface MediaObjectStorage {
	PresignedPostUpload createPresignedPost(String bucketName, String objectKey, String contentType,
			Instant expiresAt, long maxFileSize);
	String createPresignedGetUrl(String bucketName, String objectKey, Duration expiry);
	MediaObjectMetadata stat(String bucketName, String objectKey);
	byte[] readPrefix(String bucketName, String objectKey, int length);
	void delete(String bucketName, String objectKey);
}
