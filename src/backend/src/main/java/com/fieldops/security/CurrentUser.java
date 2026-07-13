package com.fieldops.security;

import com.fieldops.model.Role;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Identidad del usuario autenticado (id + rol), reconstruida a partir del
 * {@link Authentication} que {@link JwtAuthFilter} deja en el contexto de
 * seguridad. Existe para que los controllers de las historias de usuario
 * (US1-US5, todas comparten {@code OrderController}) no dupliquen la lógica
 * de extraer el rol desde las authorities de Spring Security.
 */
public record CurrentUser(UUID id, Role role) {

  public static CurrentUser from(Authentication authentication) {
    if (!(authentication.getPrincipal() instanceof UUID userId)) {
      throw new IllegalStateException("Autenticación sin un id de usuario válido");
    }
    Role role =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> Role.valueOf(authority.substring("ROLE_".length())))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Autenticación sin rol"));
    return new CurrentUser(userId, role);
  }
}
