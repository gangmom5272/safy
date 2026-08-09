package com.ojimasam.safy.auth.domain;

import java.util.Optional;
import com.ojimasam.safy.auth.domain.type.SocialProvider;

public interface SocialAccountRepository {
	SocialAccount save(SocialAccount socialAccount);
	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
	// 한 회원이 카카오·구글을 모두 연결할 수 있으므로 가장 최근에 로그인한 계정을 대표로 쓴다.
	Optional<SocialAccount> findFirstByUserIdOrderByLastLoginAtDesc(Long userId);
}
