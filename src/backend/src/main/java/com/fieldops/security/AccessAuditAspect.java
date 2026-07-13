package com.fieldops.security;

import com.fieldops.model.AccessAuditEntry;
import com.fieldops.model.AccessDenialReason;
import com.fieldops.model.User;
import com.fieldops.repository.AccessAuditEntryRepository;
import com.fieldops.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra en {@code access_audit_log} cada acceso rechazado por falta de
 * sesión (401) o rol incorrecto (403), conforme a FR-023.
 *
 * <p>Se implementa como un componente invocado explícitamente desde
 * {@code JwtAuthenticationEntryPoint}/{@code JwtAccessDeniedHandler} (nivel
 * filtro) y desde {@code GlobalExceptionHandler} (nivel controller), en vez
 * de como un {@code @Aspect} de Spring AOP: ambos puntos de entrada ya
 * concentran toda la lógica de "acceso rechazado" del sistema, así que un
 * aspecto añadiría una capa de indirección sin beneficio real de claridad.
 */
@Component
public class AccessAuditAspect {

  private final AccessAuditEntryRepository accessAuditEntryRepository;
  private final UserRepository userRepository;

  public AccessAuditAspect(
      AccessAuditEntryRepository accessAuditEntryRepository, UserRepository userRepository) {
    this.accessAuditEntryRepository = accessAuditEntryRepository;
    this.userRepository = userRepository;
  }

  /**
   * Persiste la entrada de auditoría en una transacción propia
   * (REQUIRES_NEW), para que sobreviva aunque la transacción de negocio en
   * curso (si la había) termine deshaciéndose.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordDenied(HttpServletRequest request, AccessDenialReason reason) {
    User attemptedByUser = currentUserId().map(userRepository::getReferenceById).orElse(null);
    String attemptedAction = request.getMethod() + " " + request.getRequestURI();
    accessAuditEntryRepository.save(
        new AccessAuditEntry(UUID.randomUUID(), attemptedByUser, attemptedAction, reason));
  }

  private Optional<UUID> currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof UUID userId) {
      return Optional.of(userId);
    }
    return Optional.empty();
  }
}
