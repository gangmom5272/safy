package com.ojimasam.safy.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.domain.auth.entity.AdminCredential;

public interface AdminCredentialRepository extends JpaRepository<AdminCredential, Long> {
	Optional<AdminCredential> findByLoginId(String loginId);
	boolean existsByLoginId(String loginId);
}
