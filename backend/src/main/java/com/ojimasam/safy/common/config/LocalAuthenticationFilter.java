package com.ojimasam.safy.common.config;

import java.io.IOException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ojimasam.safy.report.presentation.ReportController;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Profile("local")
public class LocalAuthenticationFilter extends OncePerRequestFilter {
	public static final String LOCAL_USER_ID_HEADER = "X-User-Id";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String userIdHeader = request.getHeader(LOCAL_USER_ID_HEADER);
		if (userIdHeader != null && !userIdHeader.isBlank()) {
			try {
				long userId = Long.parseLong(userIdHeader);
				if (userId > 0) {
					request.setAttribute(ReportController.AUTHENTICATED_USER_ID_ATTRIBUTE, userId);
				}
			} catch (NumberFormatException ignored) {
				// A missing authenticated ID is handled as 401 by the controller.
			}
		}

		filterChain.doFilter(request, response);
	}
}
