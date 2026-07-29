package com.ojimasam.safy.auth.application.dto;

public record LoginResult(TokenResponse token, String refreshToken) {
}
