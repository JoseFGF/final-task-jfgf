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
 * Regresión (hallazgo de java-reviewer en Polish, 004): {@code draft ->
 * assigned} es una transición adyacente (data-model.md), pero requiere que la
 * orden ya tenga un technician asignado (vía reasignación, feature 003) antes
 * de poder pasar a {@code assigned} manualmente. Este endpoint no acepta
 * ningún campo de technician, así que intentarlo sobre una orden {@code
 * draft} sin technician debe rechazarse, no dejar la orden en un estado
 * inconsistente (assigned sin technician).
 */
class OrderStatusManualDraftToAssignedRequiresTechnicianTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID DRAFT_ORDER_WITHOUT_TECHNICIAN_ID =
      UUID.fromString("a1111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"DISPATCHER", "SUPERVISOR"})
  void rejectsMovingDraftToAssignedWithoutATechnicianAlready(Role role) throws Exception {
    UUID userId = role == Role.DISPATCHER ? DISPATCHER_ID : SUPERVISOR_ID;
    String token = jwtService.generateToken(userId, role);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", DRAFT_ORDER_WITHOUT_TECHNICIAN_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "assigned"))))
        .andExpect(status().isUnprocessableEntity());
  }
}
