package de.omarfourati.belegfluss.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    /**
     * Hash of a random value, compared when the e-mail is unknown so that a failed
     * login takes the same time either way (no user enumeration via timing).
     */
    private final String dummyHash;

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String demoEmail;

    public UserService(AppUserRepository repository, PasswordEncoder passwordEncoder,
                       @Value("${belegfluss.demo.email:demo@belegfluss.app}") String demoEmail) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.demoEmail = demoEmail;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AppUser create(String email, String rawPassword, String displayName, Role role) {
        String normalized = AppUser.normalize(email);
        if (repository.existsByEmail(normalized)) {
            throw new EmailAlreadyUsedException(normalized);
        }
        return repository.save(new AppUser(normalized, passwordEncoder.encode(rawPassword), displayName, role));
    }

    /** Returns the user only if e-mail and password match and the account is enabled. */
    @Transactional(readOnly = true)
    public Optional<AppUser> authenticate(String email, String rawPassword) {
        Optional<AppUser> user = repository.findByEmail(AppUser.normalize(email));
        String hash = user.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(rawPassword, hash);
        return user.filter(u -> matches && u.isEnabled());
    }

    /** Changes the caller's own password after verifying the current one. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        AppUser user = repository.findById(userId).orElseThrow(WrongPasswordException::new);
        if (user.getEmail().equals(AppUser.normalize(demoEmail))) {
            throw new DemoAccountLockedException();
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return repository.findAllByOrderByCreatedAtAsc();
    }
}
