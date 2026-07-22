package com.ojimasam.safy.auth.application;

public record LoginResult(TokenResponse token, String refreshToken) {
}
