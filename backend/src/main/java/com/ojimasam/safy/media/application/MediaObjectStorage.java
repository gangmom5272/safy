package com.ojimasam.safy.media.application;

import java.time.Duration;
import java.util.List;

public interface MediaObjectStorage {
	String createPresignedPutUrl(String bucketName, String objectKey, Duration expiry);
	String createPresignedGetUrl(String bucketName, String objectKey, Duration expiry);
	MediaObjectMetadata stat(String bucketName, String objectKey);
	byte[] readPrefix(String bucketName, String objectKey, int length);
	byte[] read(String bucketName, String objectKey);
	void put(String bucketName, String objectKey, byte[] content, String contentType);
	List<StoredMediaObject> list(String bucketName, String prefix, String startAfter);
	void delete(String bucketName, String objectKey);
}
