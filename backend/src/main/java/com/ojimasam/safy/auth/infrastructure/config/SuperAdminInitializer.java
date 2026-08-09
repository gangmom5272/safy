package com.ojimasam.safy.auth.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ojimasam.safy.auth.domain.AdminCredential;
import com.ojimasam.safy.auth.domain.AdminCredentialRepository;
import com.ojimasam.safy.user.domain.User;
import com.ojimasam.safy.user.domain.type.UserRole;
import com.ojimasam.safy.user.domain.UserRepository;

@Component
public class SuperAdminInitializer implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);
	private final BootstrapAdminProperties properties;
	private final UserRepository userRepository;
	private final AdminCredentialRepository credentialRepository;
	private final PasswordEncoder passwordEncoder;

	public SuperAdminInitializer(BootstrapAdminProperties properties, UserRepository userRepository,
			AdminCredentialRepository credentialRepository, PasswordEncoder passwordEncoder) {
		this.properties = properties; this.userRepository = userRepository;
		this.credentialRepository = credentialRepository; this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!StringUtils.hasText(properties.loginId()) || !StringUtils.hasText(properties.password())) return;
		if (credentialRepository.existsByLoginId(properties.loginId())) return;
		if (properties.password().length() < 10) {
			throw new IllegalStateException("SUPER_ADMIN_PASSWORD must be at least 10 characters");
		}
		User user = userRepository.save(User.operator(properties.displayName(), UserRole.SUPER_ADMIN));
		credentialRepository.save(new AdminCredential(user, properties.loginId(),
				passwordEncoder.encode(properties.password())));
		log.info("Initial super administrator created: {}", properties.loginId());
	}
}
