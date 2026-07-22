package com.ojimasam.safy.auth.infrastructure.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import com.ojimasam.safy.auth.infrastructure.security.SafyJwtAuthenticationConverter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			SafyJwtAuthenticationConverter jwtAuthenticationConverter,
			BearerTokenResolver bearerTokenResolver,
			OAuth2LoginSuccessHandler oauth2SuccessHandler,
			OAuth2LoginFailureHandler oauth2FailureHandler) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/auth/social/*/login").permitAll()
						.requestMatchers(HttpMethod.POST,
								"/api/v1/auth/social/exchange",
								"/api/v1/auth/admin/login",
								"/api/v1/auth/token/refresh",
								"/api/v1/auth/logout").permitAll()
						.requestMatchers("/oauth2/**", "/login/oauth2/**", "/error").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth
						.bearerTokenResolver(bearerTokenResolver)
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint(new RestAuthenticationEntryPoint())
						.accessDeniedHandler(new RestAccessDeniedHandler()))
				.oauth2Login(oauth -> oauth
						.successHandler(oauth2SuccessHandler)
						.failureHandler(oauth2FailureHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new RestAuthenticationEntryPoint())
						.accessDeniedHandler(new RestAccessDeniedHandler()))
				.build();
	}

	@Bean
	BearerTokenResolver bearerTokenResolver() {
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		return request -> "/api/v1/auth/logout".equals(request.getServletPath())
				? null : delegate.resolve(request);
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	JwtEncoder jwtEncoder(AuthProperties properties) {
		return NimbusJwtEncoder.withSecretKey(secretKey(properties)).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(AuthProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("safy"));
		return decoder;
	}

	private SecretKey secretKey(AuthProperties properties) {
		byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
		return new SecretKeySpec(secret, "HmacSHA256");
	}
}
