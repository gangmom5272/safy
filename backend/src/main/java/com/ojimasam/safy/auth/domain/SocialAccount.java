package com.ojimasam.safy.auth.domain;

import java.time.Instant;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.ojimasam.safy.auth.domain.type.SocialProvider;

@Entity
@Table(name = "social_accounts", uniqueConstraints =
		@UniqueConstraint(name = "uk_social_provider_user", columnNames = {"provider", "provider_user_id"}))
public class SocialAccount extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SocialProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 255)
	private String providerUserId;

	private String email;
	// 닉네임과 프로필 이미지 URL 은 제공자가 길이를 보장하지 않는다. JPA 기본값 255 로는
	// 긴 값을 가진 계정이 신규 가입 INSERT 에서 통째로 실패해 콜백이 500 으로 끝난다
	// (2026-08-07 운영 실측: value too long for type character varying(255)).
	// ddl-auto: update 는 기존 컬럼의 길이를 넓혀주지 않으므로 운영 DB 는 ALTER 로 맞춘다.
	@Column(length = 512)
	private String providerNickname;
	@Column(length = 2048)
	private String providerProfileImageUrl;
	@Column(nullable = false, columnDefinition = "timestamp(6) with time zone")
	private Instant lastLoginAt;

	protected SocialAccount() {
	}

	public SocialAccount(User user, SocialProvider provider, String providerUserId) {
		this.user = user;
		this.provider = provider;
		this.providerUserId = providerUserId;
	}

	public void updateProfile(String email, String nickname, String imageUrl) {
		this.email = email;
		this.providerNickname = nickname;
		this.providerProfileImageUrl = imageUrl;
		this.lastLoginAt = Instant.now();
	}

	public User getUser() { return user; }

	public String getEmail() { return email; }

	public String getProviderProfileImageUrl() { return providerProfileImageUrl; }
}
