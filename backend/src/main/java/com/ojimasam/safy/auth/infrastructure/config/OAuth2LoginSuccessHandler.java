package com.ojimasam.safy.auth.infrastructure.config;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.ojimasam.safy.auth.application.AuthService;
import com.ojimasam.safy.auth.infrastructure.oauth2.OAuth2ProfileMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
	private final OAuth2ProfileMapper profileMapper;
	private final AuthService authService;
	private final AuthProperties properties;

	public OAuth2LoginSuccessHandler(OAuth2ProfileMapper profileMapper, AuthService authService,
			AuthProperties properties) {
		this.profileMapper = profileMapper; this.authService = authService; this.properties = properties;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {
		OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
		String code = authService.completeSocialLogin(token.getAuthorizedClientRegistrationId(),
				profileMapper.map(token.getAuthorizedClientRegistrationId(), token.getPrincipal()));
		if (request.getSession(false) != null) request.getSession(false).invalidate();
		response.sendRedirect(append(properties.oauth2SuccessRedirectUri(), "code", code));
	}

	private String append(String uri, String name, String value) {
		String separator = uri.contains("?") ? "&" : "?";
		return uri + separator + name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
