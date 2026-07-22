package com.ojimasam.safy.user.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Optional;

public interface UserRepository {
	User save(User user);
	Optional<User> findById(Long id);
	Page<User> findByRoleIn(Collection<UserRole> roles, Pageable pageable);
}
