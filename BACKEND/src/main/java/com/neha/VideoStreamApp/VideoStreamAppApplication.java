package com.neha.VideoStreamApp;

import com.neha.VideoStreamApp.config.AppConstants;
import com.neha.VideoStreamApp.entities.Provider;
import com.neha.VideoStreamApp.entities.Role;
import com.neha.VideoStreamApp.entities.User;
import com.neha.VideoStreamApp.repositories.RoleRepository;
import com.neha.VideoStreamApp.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnableJpaAuditing
@SpringBootApplication
public class VideoStreamAppApplication implements CommandLineRunner {

	private final Logger logger = LoggerFactory.getLogger(VideoStreamAppApplication.class);

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${app.admin.email}")
	private String adminEmail;

	@Value("${app.admin.password}")
	private String adminPassword;

	@Value("${app.admin.seed-enabled:true}")
	private boolean adminSeedEnabled;

	public static void main(String[] args) {
		SpringApplication.run(VideoStreamAppApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {

		roleRepository.findByName("ROLE_" + AppConstants.ADMIN_ROLE).ifPresentOrElse(
			role -> logger.debug("Admin role exists"),
			() -> {
				Role role = new Role();
				role.setName("ROLE_" + AppConstants.ADMIN_ROLE);
				roleRepository.save(role);
				logger.info("Admin role created");
			}
		);

		roleRepository.findByName("ROLE_" + AppConstants.USER_ROLE).ifPresentOrElse(
			role -> logger.debug("User role exists"),
			() -> {
				Role role = new Role();
				role.setName("ROLE_" + AppConstants.USER_ROLE);
				roleRepository.save(role);
				logger.info("User role created");
			}
		);

		if (!adminSeedEnabled) {
			logger.info("Admin seeding is disabled (ADMIN_SEED_ENABLED=false) — skipping default admin creation.");
			return;
		}

		if (!userRepository.existsByEmail(adminEmail)) {
			Role adminRole = roleRepository
					.findByName("ROLE_" + AppConstants.ADMIN_ROLE)
					.orElseThrow(() -> new RuntimeException("Admin Role Not Found"));

			// Password env se aaye. Agar set nahi hai to ek random strong password
			// generate karke log karo — hardcoded default password kabhi use nahi hoga.
			String effectivePassword = adminPassword;
			boolean generated = false;
			if (effectivePassword == null || effectivePassword.isBlank()) {
				effectivePassword = generateStrongPassword();
				generated = true;
			}
			validateAdminPassword(effectivePassword);

			User admin = new User();
			admin.setName("Admin");
			admin.setEmail(adminEmail);
			admin.setPassword(passwordEncoder.encode(effectivePassword));
			admin.setProvider(Provider.LOCAL);
			admin.getRoles().add(adminRole);
			userRepository.save(admin);

			if (generated) {
				logger.warn("=============================================================");
				logger.warn(" Default admin user created with a GENERATED password.");
				logger.warn(" email    : {}", adminEmail);
				logger.warn(" password : {}", effectivePassword);
				logger.warn(" Set ADMIN_PASSWORD env var to use your own password instead.");
				logger.warn("=============================================================");
			} else {
				logger.info("Default admin user created for {}", adminEmail);
			}
		}
	}

	// Random password — letters + digits + symbols mix
	private String generateStrongPassword() {
		final String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
		final String lower = "abcdefghijkmnopqrstuvwxyz";
		final String digits = "23456789";
		final String symbols = "@#$%&*?";
		final String all = upper + lower + digits + symbols;
		java.security.SecureRandom rnd = new java.security.SecureRandom();

		StringBuilder sb = new StringBuilder(16);
		sb.append(upper.charAt(rnd.nextInt(upper.length())));
		sb.append(lower.charAt(rnd.nextInt(lower.length())));
		sb.append(digits.charAt(rnd.nextInt(digits.length())));
		sb.append(symbols.charAt(rnd.nextInt(symbols.length())));
		for (int i = 0; i < 12; i++) {
			sb.append(all.charAt(rnd.nextInt(all.length())));
		}
		// shuffle taaki pattern predict na ho
		char[] chars = sb.toString().toCharArray();
		for (int i = chars.length - 1; i > 0; i--) {
			int j = rnd.nextInt(i + 1);
			char tmp = chars[i];
			chars[i] = chars[j];
			chars[j] = tmp;
		}
		return new String(chars);
	}

	// Admin password bhi app ke same password policy ko follow kare
	private void validateAdminPassword(String password) {
		if (password == null || password.length() < 8) {
			throw new IllegalStateException("ADMIN_PASSWORD must be at least 8 characters long");
		}
		if (!password.matches(".*[A-Za-z].*")) {
			throw new IllegalStateException("ADMIN_PASSWORD must contain at least one letter");
		}
		if (!password.matches(".*\\d.*")) {
			throw new IllegalStateException("ADMIN_PASSWORD must contain at least one number");
		}
		if (!password.matches(".*[^A-Za-z0-9].*")) {
			throw new IllegalStateException("ADMIN_PASSWORD must contain at least one special character");
		}
	}
}
