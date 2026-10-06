package de.omarfourati.belegfluss.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from the environment and, if enabled, a read-only demo account.
 * Runs on every start but never overwrites existing users.
 */
@Component
public class UserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    private final UserService userService;
    private final AppUserRepository repository;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean demoEnabled;
    private final String demoEmail;
    private final String demoPassword;

    public UserBootstrap(UserService userService, AppUserRepository repository,
                         @Value("${belegfluss.admin.email:}") String adminEmail,
                         @Value("${belegfluss.admin.password:}") String adminPassword,
                         @Value("${belegfluss.demo.enabled:false}") boolean demoEnabled,
                         @Value("${belegfluss.demo.email:demo@belegfluss.app}") String demoEmail,
                         @Value("${belegfluss.demo.password:demo-belegfluss}") String demoPassword) {
        this.userService = userService;
        this.repository = repository;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.demoEnabled = demoEnabled;
        this.demoEmail = demoEmail;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!adminEmail.isBlank() && !adminPassword.isBlank() && !repository.existsByRole(Role.ADMIN)) {
            if (adminPassword.length() < 12) {
                throw new IllegalStateException("belegfluss.admin.password must have at least 12 characters");
            }
            userService.create(adminEmail, adminPassword, "Administrator", Role.ADMIN);
            log.info("Created initial admin {}", adminEmail);
        }
        if (demoEnabled && !repository.existsByEmail(AppUser.normalize(demoEmail))) {
            userService.create(demoEmail, demoPassword, "Demo (read-only)", Role.VIEWER);
            log.info("Created read-only demo account {}", demoEmail);
        }
    }
}
