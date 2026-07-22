package com.ojimasam.safy.domain.auth.social;

public record SocialUserInfo(String providerUserId, String email, String nickname, String profileImageUrl) {
}
