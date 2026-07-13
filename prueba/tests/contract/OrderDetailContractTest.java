import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prueba.model.Role;
import com.prueba.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test de {@code GET /orders/{orderId}} contra
 * prueba/contracts/openapi.yaml (FR-001 a FR-004): 200 con el schema
 * {@code OrderDetail}, 401 sin sesión, 403 cuando el rol no tiene motivo
 * para ver esa orden (FR-004), 404 si no existe.
 */
class OrderDetailContractTest extends BaseIntegrationTest {

  private static final UUID SEED_DISPATCHER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SEED_TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID PENDING_REVIEW_ORDER_ID =
      UUID.fromString("a4444444-4444-4444-4444-444444444444");
  private static final UUID UNASSIGNED_DRAFT_ORDER_ID =
      UUID.fromString("a1111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;

  @Test
  void getOrderDetailReturnsDetailSchema() throws Exception {
    String token = jwtService.generateToken(SEED_DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", PENDING_REVIEW_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(PENDING_REVIEW_ORDER_ID.toString()))
        .andExpect(jsonPath("$.status").value("pending_review"))
        .andExpect(jsonPath("$.executionNote").exists())
        .andExpect(jsonPath("$.evidencePhotoIds").isArray());
  }

  @Test
  void getOrderDetailWithoutSessionIsRejected() throws Exception {
    mockMvc
        .perform(get("/api/v1/orders/{orderId}", PENDING_REVIEW_ORDER_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void technicianCannotViewAnOrderNotAssignedToThem() throws Exception {
    String token = jwtService.generateToken(SEED_TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", UNASSIGNED_DRAFT_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void getOrderDetailOfUnknownOrderReturnsNotFound() throws Exception {
    String token = jwtService.generateToken(SEED_DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }
}
