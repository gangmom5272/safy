package com.ojimasam.safy.domain.auth.dto;

public record LoginResult(TokenResponse token, String refreshToken) {
}
