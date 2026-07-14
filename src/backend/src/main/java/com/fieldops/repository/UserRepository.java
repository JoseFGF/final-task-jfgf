package com.fieldops.repository;

import com.fieldops.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  /** Búsqueda insensible a mayúsculas/minúsculas (FR-003, ADR-005). */
  Optional<User> findByEmailIgnoreCase(String email);
}
