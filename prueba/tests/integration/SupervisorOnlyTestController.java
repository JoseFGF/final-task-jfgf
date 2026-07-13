import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller mínimo, usado únicamente por tests de la base común
 * ({@link AccessAuditTest}, {@link AuthLoginIntegrationTest}), que ejercita
 * el mecanismo real de seguridad ({@code JwtAuthFilter} +
 * {@code @EnableMethodSecurity} +
 * {@code GlobalExceptionHandler}/{@code JwtAuthenticationEntryPoint}) sin
 * depender de ningún controller de negocio como {@code /orders} (implementado
 * por otro agente, fuera de esta base común).
 */
@RestController
public class SupervisorOnlyTestController {

  @GetMapping("/test-support/supervisor-only")
  @PreAuthorize("hasRole('SUPERVISOR')")
  public ResponseEntity<Void> supervisorOnly() {
    return ResponseEntity.ok().build();
  }

  /** Cualquier rol autenticado; solo exige que el JWT sea válido. */
  @GetMapping("/test-support/authenticated-only")
  public ResponseEntity<Void> authenticatedOnly() {
    return ResponseEntity.ok().build();
  }
}
