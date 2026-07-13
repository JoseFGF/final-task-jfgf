import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fieldops.model.Role;
import com.fieldops.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T015 (US1): contract test de {@code GET /orders/{orderId}} — 200, 401,
 * 403 (FR-004) y 404, y verifica que {@code rejectionComment} viaja en el
 * JSON (FR-022) aunque sea {@code null}.
 */
class OrderDetailContractTest extends BaseIntegrationTest {

  private static final UUID SEED_DISPATCHER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SEED_TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  // Asignada a technician2 (44444444...), no al technician de seed.
  private static final UUID ORDER_NOT_ASSIGNED_TO_SEED_TECHNICIAN =
      UUID.fromString("a5555555-5555-5555-5555-555555555555");
  private static final UUID ORDER_ASSIGNED_TO_SEED_TECHNICIAN =
      UUID.fromString("a3333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;

  @Test
  void returnsOrderDetailForAuthorizedUser() throws Exception {
    String token = jwtService.generateToken(SEED_DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", ORDER_ASSIGNED_TO_SEED_TECHNICIAN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ORDER_ASSIGNED_TO_SEED_TECHNICIAN.toString()));
  }

  @Test
  void rejectionCommentFieldIsPresentAsNullWhenOrderWasNeverRejected() throws Exception {
    String token = jwtService.generateToken(SEED_DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", ORDER_ASSIGNED_TO_SEED_TECHNICIAN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rejectionComment").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void rejectsRequestWithoutSession() throws Exception {
    mockMvc
        .perform(get("/api/v1/orders/{orderId}", ORDER_ASSIGNED_TO_SEED_TECHNICIAN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsTechnicianAccessingOrderNotAssignedToThem() throws Exception {
    String token = jwtService.generateToken(SEED_TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", ORDER_NOT_ASSIGNED_TO_SEED_TECHNICIAN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsNotFoundForAnUnknownOrder() throws Exception {
    String token = jwtService.generateToken(SEED_DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }
}
