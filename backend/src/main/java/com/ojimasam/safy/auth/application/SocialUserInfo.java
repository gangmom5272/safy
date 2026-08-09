package com.ojimasam.safy.auth.application;

public record SocialUserInfo(String providerUserId, String email, String nickname, String profileImageUrl) {
}
