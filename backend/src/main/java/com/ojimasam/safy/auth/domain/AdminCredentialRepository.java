package com.ojimasam.safy.auth.domain;

import java.util.Optional;

public interface AdminCredentialRepository {
	AdminCredential save(AdminCredential credential);
	Optional<AdminCredential> findById(Long id);
	Optional<AdminCredential> findByLoginId(String loginId);
	boolean existsByLoginId(String loginId);
}
