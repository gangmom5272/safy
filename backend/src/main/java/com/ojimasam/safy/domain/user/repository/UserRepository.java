package com.ojimasam.safy.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

import com.ojimasam.safy.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
	Page<User> findByRoleIn(Collection<com.ojimasam.safy.domain.user.entity.UserRole> roles, Pageable pageable);
}
