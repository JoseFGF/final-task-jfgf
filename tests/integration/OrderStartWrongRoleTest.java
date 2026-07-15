import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Role;
import com.fieldops.security.JwtService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T006 (US1): dado que {@code POST /orders/{orderId}/status} es un único
 * endpoint compartido (ADR-009), no existe una "acción de iniciar trabajo"
 * separada que rechace a dispatcher/supervisor por su rol en sí mismo —
 * dispatcher/supervisor SÍ pueden pedir {@code assigned -> in_progress} (ver
 * User Story 2, T012). Lo que este test verifica es que la regla de
 * adyacencia (ADR-010) se aplica igual sin importar el rol: una transición
 * no adyacente (aquí {@code draft -> pending_review}, saltándose
 * {@code assigned} e {@code in_progress}) se rechaza con 409 tanto para
 * DISPATCHER como para SUPERVISOR, no solo para technicians.
 */
class OrderStartWrongRoleTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID DRAFT_ORDER_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"DISPATCHER", "SUPERVISOR"})
  void nonAdjacentTransitionIsRejectedRegardlessOfRole(Role role) throws Exception {
    UUID userId = role == Role.DISPATCHER ? DISPATCHER_ID : SUPERVISOR_ID;
    String token = jwtService.generateToken(userId, role);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", DRAFT_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "pending_review"))))
        .andExpect(status().isConflict());
  }
}
