package com.ojimasam.safy.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.auth.domain.AdminCredential;
import com.ojimasam.safy.auth.domain.AdminCredentialRepository;

public interface JpaAdminCredentialRepository
		extends JpaRepository<AdminCredential, Long>, AdminCredentialRepository {
}
