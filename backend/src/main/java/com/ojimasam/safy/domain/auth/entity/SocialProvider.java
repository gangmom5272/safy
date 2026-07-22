package com.ojimasam.safy.domain.auth.entity;

public enum SocialProvider {
	KAKAO,
	GOOGLE;

	public static SocialProvider from(String value) {
		return valueOf(value.toUpperCase());
	}
}
