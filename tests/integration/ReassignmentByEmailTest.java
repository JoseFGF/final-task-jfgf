import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.JwtService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T004 (US1): reasignar con el email de un technician existente, incluida
 * distinta capitalización y espacios al principio/final, resuelve
 * correctamente al technician (FR-003, SC-001).
 */
class ReassignmentByEmailTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void resolvesTechnicianRegardlessOfEmailCapitalization() throws Exception {
    Order order = createOrder();
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(reassignmentRequest(order.getId(), token, "Technician2@Fieldops.TEST"))
        .andExpect(status().isOk());

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getAssignedTechnician().getId()).isEqualTo(OTHER_TECHNICIAN_ID);
  }

  @Test
  void resolvesTechnicianTrimmingLeadingAndTrailingWhitespace() throws Exception {
    Order order = createOrder();
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(reassignmentRequest(order.getId(), token, "  technician2@fieldops.test  "))
        .andExpect(status().isOk());

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getAssignedTechnician().getId()).isEqualTo(OTHER_TECHNICIAN_ID);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      reassignmentRequest(UUID orderId, String token, String newTechnicianEmail) throws Exception {
    return post("/api/v1/orders/{orderId}/reassignment", orderId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("newTechnicianEmail", newTechnicianEmail)));
  }

  private Order createOrder() {
    Order order =
        new Order(UUID.randomUUID(), OrderStatus.assigned, userRepository.getReferenceById(TECHNICIAN_ID));
    return orderRepository.save(order);
  }
}
