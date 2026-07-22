package com.ojimasam.safy.auth.domain;

import java.util.Optional;

public interface SocialAccountRepository {
	SocialAccount save(SocialAccount socialAccount);
	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
