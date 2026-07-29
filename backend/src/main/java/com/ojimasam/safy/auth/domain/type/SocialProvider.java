package com.ojimasam.safy.auth.domain.type;

public enum SocialProvider {
	KAKAO,
	GOOGLE;

	public static SocialProvider from(String value) {
		return valueOf(value.toUpperCase());
	}
}
