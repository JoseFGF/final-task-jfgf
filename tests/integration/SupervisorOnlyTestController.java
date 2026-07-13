import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller mínimo, usado únicamente por {@link AccessAuditTest} (T010a),
 * que ejercita el mecanismo real de seguridad
 * ({@code JwtAuthFilter} + {@code @EnableMethodSecurity} +
 * {@code GlobalExceptionHandler}/{@code JwtAuthenticationEntryPoint}) sin
 * depender de ningún controller de negocio (fuera de alcance de esta fase).
 */
@RestController
public class SupervisorOnlyTestController {

  @GetMapping("/test-support/supervisor-only")
  @PreAuthorize("hasRole('SUPERVISOR')")
  public ResponseEntity<Void> supervisorOnly() {
    return ResponseEntity.ok().build();
  }
}
