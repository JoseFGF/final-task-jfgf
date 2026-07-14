import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.OrderStatus;
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
 * T019 (US2): dispatcher/supervisor intentan cambiar una orden {@code closed}
 * a cualquier otro estado -> 409, {@code closed} permanece terminal (FR-006,
 * SC-004). Usa la orden seed {@code a5555555} ({@code closed}).
 */
class OrderStatusManualClosedTerminalTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CLOSED_ORDER_ID = UUID.fromString("a5555555-5555-5555-5555-555555555555");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"draft", "assigned", "in_progress", "pending_review"})
  void closedOrderCannotBeMovedToAnyOtherStatus(OrderStatus target) throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", CLOSED_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", target.name()))))
        .andExpect(status().isConflict());
  }
}
