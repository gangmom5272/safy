package com.ojimasam.safy.auth.infrastructure.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {
	private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);
	private final AuthProperties properties;
	public OAuth2LoginFailureHandler(AuthProperties properties) { this.properties = properties; }

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {
		log.error("OAuth2 login failed on callback {}: {}", request.getRequestURI(),
				exception.getMessage(), exception);
		if (request.getSession(false) != null) request.getSession(false).invalidate();
		String separator = properties.oauth2FailureRedirectUri().contains("?") ? "&" : "?";
		response.sendRedirect(properties.oauth2FailureRedirectUri() + separator + "error=oauth2_login_failed");
	}
}
