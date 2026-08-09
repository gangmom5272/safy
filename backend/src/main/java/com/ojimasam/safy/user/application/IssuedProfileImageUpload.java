package com.ojimasam.safy.user.application;

import java.time.Instant;

public record IssuedProfileImageUpload(String uploadUrl, String contentType, long maxFileSize, Instant expiresAt) {
}
