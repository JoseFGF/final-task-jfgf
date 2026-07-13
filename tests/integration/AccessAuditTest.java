import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fieldops.model.AccessAuditEntry;
import com.fieldops.model.AccessDenialReason;
import com.fieldops.model.Role;
import com.fieldops.repository.AccessAuditEntryRepository;
import com.fieldops.security.JwtService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T010a: un acceso rechazado por falta de sesión (401) o por rol incorrecto
 * (403) debe escribir una fila en {@code access_audit_log} con
 * {@code attemptedAction} y {@code reason} correctos (FR-023).
 *
 * <p>Como esta fase (Setup + Foundational) no incluye ningún controller de
 * negocio, se importa {@link SupervisorOnlyTestController} —un endpoint
 * mínimo protegido con {@code @PreAuthorize("hasRole('SUPERVISOR')")}, solo
 * para este test— que ejercita exactamente el mismo mecanismo de seguridad
 * ({@code JwtAuthFilter} + {@code @EnableMethodSecurity} +
 * {@code GlobalExceptionHandler}/{@code JwtAuthenticationEntryPoint}) que
 * usarán los controllers reales de historias de usuario futuras.
 */
@Import(SupervisorOnlyTestController.class)
class AccessAuditTest extends BaseIntegrationTest {

  private static final String PROTECTED_PATH = "/test-support/supervisor-only";
  private static final UUID SEED_TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private AccessAuditEntryRepository accessAuditEntryRepository;

  @Test
  void accessWithoutSessionIsRejectedAndAudited() throws Exception {
    mockMvc.perform(get(PROTECTED_PATH)).andExpect(status().isUnauthorized());

    AccessAuditEntry entry = lastAuditEntryFor("GET " + PROTECTED_PATH);
    assertThat(entry.getReason()).isEqualTo(AccessDenialReason.NO_SESSION);
    assertThat(entry.getAttemptedByUser()).isNull();
  }

  @Test
  void accessWithWrongRoleIsRejectedAndAudited() throws Exception {
    String token = jwtService.generateToken(SEED_TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());

    AccessAuditEntry entry = lastAuditEntryFor("GET " + PROTECTED_PATH);
    assertThat(entry.getReason()).isEqualTo(AccessDenialReason.ROLE_NOT_ALLOWED);
    assertThat(entry.getAttemptedByUser()).isNotNull();
    assertThat(entry.getAttemptedByUser().getId()).isEqualTo(SEED_TECHNICIAN_ID);
  }

  private AccessAuditEntry lastAuditEntryFor(String attemptedAction) {
    List<AccessAuditEntry> entries =
        accessAuditEntryRepository.findAllByAttemptedAction(attemptedAction);
    assertThat(entries).isNotEmpty();
    return entries.get(entries.size() - 1);
  }
}
