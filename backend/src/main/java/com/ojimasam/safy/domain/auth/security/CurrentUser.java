package com.ojimasam.safy.domain.auth.security;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {
	private CurrentUser() {
	}

	public static Long id(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
