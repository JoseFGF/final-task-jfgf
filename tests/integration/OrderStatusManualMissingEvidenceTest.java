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
 * T013 (US2): dispatcher/supervisor intentan {@code in_progress ->
 * pending_review} sobre una orden sin ninguna foto de evidencia registrada
 * -> 422 (FR-005, SC-003). Reutiliza la orden seed {@code a3333333}
 * ({@code in_progress}, sin fotos de evidencia).
 */
class OrderStatusManualMissingEvidenceTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID IN_PROGRESS_ORDER_WITHOUT_EVIDENCE_ID =
      UUID.fromString("a3333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"DISPATCHER", "SUPERVISOR"})
  void movingToPendingReviewWithoutEvidenceIsRejected(Role role) throws Exception {
    UUID userId = role == Role.DISPATCHER ? DISPATCHER_ID : SUPERVISOR_ID;
    String token = jwtService.generateToken(userId, role);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", IN_PROGRESS_ORDER_WITHOUT_EVIDENCE_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "pending_review"))))
        .andExpect(status().isUnprocessableEntity());
  }
}
