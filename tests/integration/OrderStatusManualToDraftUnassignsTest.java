import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
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
 * T015 (US2): dispatcher/supervisor cambian manualmente {@code assigned ->
 * draft} -> 200 y el technician queda desvinculado
 * ({@code assignedTechnicianEmail: null}, data-model.md).
 */
class OrderStatusManualToDraftUnassignsTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID ASSIGNED_ORDER_ID =
      UUID.fromString("a2222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private ObjectMapper objectMapper;

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"DISPATCHER", "SUPERVISOR"})
  void movingBackToDraftUnassignsTheTechnician(Role role) throws Exception {
    UUID userId = role == Role.DISPATCHER ? DISPATCHER_ID : SUPERVISOR_ID;
    String token = jwtService.generateToken(userId, role);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", ASSIGNED_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "draft"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("draft"))
        .andExpect(jsonPath("$.assignedTechnicianEmail").isEmpty());

    Order reloaded = orderRepository.findById(ASSIGNED_ORDER_ID).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.draft);
    assertThat(reloaded.getAssignedTechnician()).isNull();
  }
}
