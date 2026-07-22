package com.ojimasam.safy.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.UserRepository;

public interface JpaUserRepository extends JpaRepository<User, Long>, UserRepository {
}
