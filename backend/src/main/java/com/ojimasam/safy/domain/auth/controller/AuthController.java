package com.ojimasam.safy.domain.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import com.ojimasam.safy.common.api.ApiResponse;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.domain.auth.config.AuthProperties;
import com.ojimasam.safy.domain.auth.dto.AdminLoginRequest;
import com.ojimasam.safy.domain.auth.dto.LoginResult;
import com.ojimasam.safy.domain.auth.dto.SocialCodeExchangeRequest;
import com.ojimasam.safy.domain.auth.dto.TokenResponse;
import com.ojimasam.safy.domain.auth.exception.AuthErrorCode;
import com.ojimasam.safy.domain.auth.service.AuthService;
import com.ojimasam.safy.domain.auth.security.LogoutAccessTokenService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "소셜 로그인, 관리자 로그인, 토큰 재발급 및 로그아웃")
public class AuthController {
	private final AuthService authService;
	private final AuthProperties properties;
	private final LogoutAccessTokenService logoutAccessTokenService;

	public AuthController(AuthService authService, AuthProperties properties,
			LogoutAccessTokenService logoutAccessTokenService) {
		this.authService = authService;
		this.properties = properties;
		this.logoutAccessTokenService = logoutAccessTokenService;
	}

	@GetMapping("/social/{provider}/login")
	@Operation(summary = "소셜 로그인 시작", description = "Google 또는 Kakao 로그인 페이지로 리다이렉트합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "소셜 제공자 로그인 페이지로 이동")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "지원하지 않는 소셜 제공자")
	public ResponseEntity<Void> socialLogin(
			@Parameter(description = "소셜 제공자", example = "kakao", schema = @Schema(allowableValues = {"google", "kakao"}))
			@org.springframework.web.bind.annotation.PathVariable String provider) {
		String normalized = provider.toLowerCase();
		if (!normalized.equals("kakao") && !normalized.equals("google"))
			throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
		return ResponseEntity.status(302).location(java.net.URI.create("/oauth2/authorization/" + normalized)).build();
	}

	@PostMapping("/social/exchange")
	@Operation(summary = "소셜 로그인 코드 교환", description = "OAuth2 로그인 완료 후 프론트로 전달된 60초 일회용 코드를 SAFY 토큰으로 교환합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Access Token 발급 및 Refresh Token 쿠키 설정")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 일회용 코드")
	public ResponseEntity<ApiResponse<TokenResponse>> exchangeSocialCode(
			@Valid @RequestBody SocialCodeExchangeRequest request) {
		return loginResponse(authService.exchangeSocialLoginCode(request.code()));
	}

	@PostMapping("/admin/login")
	@Operation(summary = "관리자 로컬 로그인", description = "관리자 아이디와 비밀번호를 검증하고 토큰을 발급합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치")
	public ResponseEntity<ApiResponse<TokenResponse>> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
		return loginResponse(authService.adminLogin(request.loginId(), request.password()));
	}

	@PostMapping("/token/refresh")
	@Operation(summary = "Access·Refresh Token 재발급", description = "HttpOnly Refresh Token 쿠키를 검증하고 토큰을 회전합니다.",
			parameters = @Parameter(name = "SAFY_REFRESH_TOKEN", in = ParameterIn.COOKIE, required = true,
					description = "로그인 시 발급된 Refresh Token"))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 재발급 성공")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh Token 누락·만료·위조")
	public ResponseEntity<ApiResponse<TokenResponse>> refresh(
			@Parameter(hidden = true)
			@CookieValue(name = "SAFY_REFRESH_TOKEN", required = false) String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
		return loginResponse(authService.refresh(refreshToken));
	}

	@PostMapping("/logout")
	@Operation(summary = "현재 기기 로그아웃", description = "Bearer Access Token이 전달되면 즉시 폐기하고, Refresh Token과 쿠키를 삭제합니다.",
			parameters = @Parameter(name = "SAFY_REFRESH_TOKEN", in = ParameterIn.COOKIE,
					description = "선택 사항인 Refresh Token"))
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 완료")
	public ResponseEntity<ApiResponse<Void>> logout(
			@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@Parameter(hidden = true)
			@CookieValue(name = "SAFY_REFRESH_TOKEN", required = false) String refreshToken) {
		logoutAccessTokenService.revokeIfValid(authorization);
		authService.logout(refreshToken);
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, deleteCookie().toString())
				.body(ApiResponse.successWithoutData());
	}

	private ResponseEntity<ApiResponse<TokenResponse>> loginResponse(LoginResult result) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
				.body(ApiResponse.success(result.token()));
	}

	private ResponseCookie refreshCookie(String token) {
		return cookie(token).maxAge(properties.refreshTokenTtl()).build();
	}

	private ResponseCookie deleteCookie() {
		return cookie("").maxAge(0).build();
	}

	private ResponseCookie.ResponseCookieBuilder cookie(String value) {
		return ResponseCookie.from(properties.refreshCookieName(), value)
				.httpOnly(true).secure(properties.cookieSecure()).sameSite(properties.cookieSameSite())
				.path("/api/v1/auth");
	}
}
