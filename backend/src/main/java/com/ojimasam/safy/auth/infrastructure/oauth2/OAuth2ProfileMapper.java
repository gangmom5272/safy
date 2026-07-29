package com.ojimasam.safy.auth.infrastructure.oauth2;

import java.util.Map;
import java.util.HashMap;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.auth.application.SocialUserInfo;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.auth.domain.type.SocialProvider;
import com.ojimasam.safy.auth.domain.type.AuthErrorCode;

@Component
public class OAuth2ProfileMapper {
	public SocialUserInfo map(String registrationId, OAuth2User principal) {
		SocialProvider provider;
		try { provider = SocialProvider.from(registrationId); }
		catch (IllegalArgumentException exception) { throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER); }

		return switch (provider) {
			case GOOGLE -> google(principal.getAttributes());
			case KAKAO -> kakao(principal.getAttributes());
		};
	}

	private SocialUserInfo google(Map<String, Object> attributes) {
		return new SocialUserInfo(required(attributes, "sub"), text(attributes, "email"),
				text(attributes, "name"), text(attributes, "picture"));
	}

	private SocialUserInfo kakao(Map<String, Object> attributes) {
		Map<String, Object> account = map(attributes.get("kakao_account"));
		Map<String, Object> profile = map(account.get("profile"));
		return new SocialUserInfo(required(attributes, "id"), text(account, "email"),
				text(profile, "nickname"), text(profile, "profile_image_url"));
	}

	private Map<String, Object> map(Object value) {
		if (!(value instanceof Map<?, ?> source)) return Map.of();
		Map<String, Object> result = new HashMap<>();
		source.forEach((key, nestedValue) -> {
			if (key instanceof String stringKey) result.put(stringKey, nestedValue);
		});
		return result;
	}

	private String required(Map<String, Object> values, String key) {
		String value = text(values, key);
		if (value == null || value.isBlank()) throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
		return value;
	}

	private String text(Map<String, Object> values, String key) {
		Object value = values.get(key);
		return value == null ? null : value.toString();
	}
}
