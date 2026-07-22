package com.ojimasam.safy.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.domain.auth.entity.SocialAccount;
import com.ojimasam.safy.domain.auth.entity.SocialProvider;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
