package de.omarfourati.belegfluss.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    List<AppUser> findAllByOrderByCreatedAtAsc();

    boolean existsByIdAndEnabledTrue(UUID id);
}
