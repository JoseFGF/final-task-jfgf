import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.security.JwtService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T002 (US1): contract test de {@code POST /orders/{orderId}/status} contra
 * contracts/openapi.yaml — esqueleto básico de forma de respuesta y códigos
 * (200/401/403/404/409, FR-001 a FR-003).
 */
class OrderStatusContractTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID ASSIGNED_ORDER_ID =
      UUID.fromString("a2222222-2222-2222-2222-222222222222");
  private static final UUID IN_PROGRESS_ORDER_ID =
      UUID.fromString("a3333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void changingToAnAllowedStatusReturnsOk() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(statusRequest(ASSIGNED_ORDER_ID, token, OrderStatus.in_progress))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsRequestWithoutSession() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", ASSIGNED_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "in_progress"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsTechnicianNotAssignedToTheOrder() throws Exception {
    String token = jwtService.generateToken(OTHER_TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(statusRequest(ASSIGNED_ORDER_ID, token, OrderStatus.in_progress))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsNotFoundForAnUnknownOrder() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(statusRequest(UUID.randomUUID(), token, OrderStatus.in_progress))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsATransitionFromAnInvalidOriginState() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(statusRequest(IN_PROGRESS_ORDER_ID, token, OrderStatus.in_progress))
        .andExpect(status().isConflict());
  }

  private MockHttpServletRequestBuilder statusRequest(UUID orderId, String token, OrderStatus target)
      throws Exception {
    return post("/api/v1/orders/{orderId}/status", orderId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("status", target.name())));
  }
}
