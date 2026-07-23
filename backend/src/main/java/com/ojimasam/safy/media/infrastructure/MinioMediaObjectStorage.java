package com.ojimasam.safy.media.infrastructure;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.media.application.MediaObjectMetadata;
import com.ojimasam.safy.media.application.MediaObjectStorage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;

@Component
public class MinioMediaObjectStorage implements MediaObjectStorage {
	private final MinioClient minioClient;
	private final MinioClient presignClient;
	private final String publicEndpoint;

	public MinioMediaObjectStorage(
			MinioClient minioClient,
			@Qualifier("minioPresignClient") MinioClient presignClient,
			@Value("${minio.public-endpoint:${minio.endpoint}}") String publicEndpoint
	) {
		this.minioClient = minioClient;
		this.presignClient = presignClient;
		this.publicEndpoint = publicEndpoint.replaceAll("/+$", "");
	}

	@Override
	public String createPresignedPutUrl(String bucketName, String objectKey, Duration expiry) {
		try {
			ensureBucket(bucketName);
			return presignClient.getPresignedObjectUrl(
					GetPresignedObjectUrlArgs.builder()
							.method(Method.PUT)
							.bucket(bucketName)
							.object(objectKey)
							.expiry(Math.toIntExact(expiry.toSeconds()), TimeUnit.SECONDS)
							.build()
			);
		} catch (Exception exception) {
			throw new IllegalStateException("이미지 업로드 URL을 생성하지 못했습니다.", exception);
		}
	}

	@Override
	public MediaObjectMetadata stat(String bucketName, String objectKey) {
		try {
			StatObjectResponse response = minioClient.statObject(
					StatObjectArgs.builder().bucket(bucketName).object(objectKey).build()
			);
			return new MediaObjectMetadata(response.size(), response.contentType());
		} catch (Exception exception) {
			throw new IllegalStateException("업로드된 이미지 객체를 확인하지 못했습니다.", exception);
		}
	}

	@Override
	public byte[] readPrefix(String bucketName, String objectKey, int length) {
		try (GetObjectResponse response = minioClient.getObject(
				GetObjectArgs.builder().bucket(bucketName).object(objectKey).offset(0L).length((long) length).build()
		)) {
			return response.readNBytes(length);
		} catch (Exception exception) {
			throw new IllegalStateException("업로드된 이미지 내용을 확인하지 못했습니다.", exception);
		}
	}

	@Override
	public void delete(String bucketName, String objectKey) {
		try {
			minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectKey).build());
		} catch (Exception exception) {
			throw new IllegalStateException("만료된 이미지 객체를 삭제하지 못했습니다.", exception);
		}
	}

	@Override
	public String createPresignedGetUrl(String bucketName, String objectKey, Duration expiry) {
		try {
			return presignClient.getPresignedObjectUrl(
					GetPresignedObjectUrlArgs.builder()
							.method(Method.GET)
							.bucket(bucketName)
							.object(objectKey)
							.expiry(Math.toIntExact(expiry.toSeconds()), TimeUnit.SECONDS)
							.build()
			);
		} catch (Exception exception) {
			throw new IllegalStateException("이미지 조회 URL을 생성하지 못했습니다.", exception);
		}
	}

	private void ensureBucket(String bucketName) throws Exception {
		boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
		if (!exists) {
			minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
		}
	}
}
