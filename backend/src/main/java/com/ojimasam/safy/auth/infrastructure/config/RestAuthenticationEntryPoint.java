package com.ojimasam.safy.auth.infrastructure.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException, ServletException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"success\":false,\"code\":\"AUTH_008\",\"message\":\"인증이 필요합니다.\",\"fieldErrors\":[],\"path\":\""
				+ escape(request.getRequestURI()) + "\"}");
	}

	private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
