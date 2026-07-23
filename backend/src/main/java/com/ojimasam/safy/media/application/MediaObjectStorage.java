package com.ojimasam.safy.media.application;

import java.time.Duration;

public interface MediaObjectStorage {
	String createPresignedPutUrl(String bucketName, String objectKey, Duration expiry);
	String createPresignedGetUrl(String bucketName, String objectKey, Duration expiry);
	MediaObjectMetadata stat(String bucketName, String objectKey);
	byte[] readPrefix(String bucketName, String objectKey, int length);
	void delete(String bucketName, String objectKey);
}
