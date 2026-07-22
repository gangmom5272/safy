package com.ojimasam.safy.auth.infrastructure.config;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RestAccessDeniedHandler implements AccessDeniedHandler {
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException, ServletException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"success\":false,\"code\":\"AUTH_007\",\"message\":\"해당 요청에 대한 권한이 없습니다.\",\"fieldErrors\":[],\"path\":\""
				+ escape(request.getRequestURI()) + "\"}");
	}

	private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
