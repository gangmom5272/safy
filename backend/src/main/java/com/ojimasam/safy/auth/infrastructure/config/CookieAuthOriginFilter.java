package com.ojimasam.safy.auth.infrastructure.config;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ojimasam.safy.common.config.CorsProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CookieAuthOriginFilter extends OncePerRequestFilter {
	private static final Set<String> PROTECTED_PATHS = Set.of(
			"/api/v1/auth/token/refresh",
			"/api/v1/auth/admin/token/refresh",
			"/api/v1/auth/logout",
			"/api/v1/auth/admin/logout"
	);

	private final Set<String> allowedOrigins;

	public CookieAuthOriginFilter(CorsProperties properties) {
		this.allowedOrigins = Set.copyOf(properties.allowedOrigins());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		if (requiresOriginCheck(request) && origin != null && !allowedOrigins.contains(origin)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write("{\"success\":false,\"code\":\"AUTH_007\","
					+ "\"message\":\"허용되지 않은 출처입니다.\",\"fieldErrors\":[],\"path\":\""
					+ escape(request.getRequestURI()) + "\"}");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean requiresOriginCheck(HttpServletRequest request) {
		return HttpMethod.POST.matches(request.getMethod()) && PROTECTED_PATHS.contains(request.getServletPath());
	}

	private String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
