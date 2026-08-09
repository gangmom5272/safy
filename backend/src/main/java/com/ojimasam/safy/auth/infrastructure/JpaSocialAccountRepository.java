package com.ojimasam.safy.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.auth.domain.SocialAccount;
import com.ojimasam.safy.auth.domain.SocialAccountRepository;

public interface JpaSocialAccountRepository extends JpaRepository<SocialAccount, Long>, SocialAccountRepository {
}
