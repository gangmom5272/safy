package com.ojimasam.safy.auth.domain;

import java.util.Optional;
import com.ojimasam.safy.auth.domain.type.SocialProvider;

public interface SocialAccountRepository {
	SocialAccount save(SocialAccount socialAccount);
	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
