import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Role;
import com.fieldops.security.JwtService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T020 (US2): un technician intenta usar el cambio manual de estado fuera de
 * su única transición permitida -> 403 (FR-007, el cambio manual es
 * exclusivo de dispatcher/supervisor).
 */
class OrderStatusManualForbiddenForTechnicianTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ASSIGNED_ORDER_ID =
      UUID.fromString("a2222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void technicianRequestingAManualBackwardMoveOnItsOwnOrderIsForbidden() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", ASSIGNED_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "draft"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void technicianRequestingAChangeOnAnOrderNotAssignedToItIsForbidden() throws Exception {
    // technician@fieldops.test no tiene asignada la orden a5555555 (closed,
    // asignada a technician2@fieldops.test).
    UUID orderNotAssignedToThisTechnician =
        UUID.fromString("a5555555-5555-5555-5555-555555555555");
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", orderNotAssignedToThisTechnician)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "in_progress"))))
        .andExpect(status().isForbidden());
  }
}
