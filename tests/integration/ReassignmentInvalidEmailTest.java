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
 * T005 (US1): reasignar con un email que no corresponde a ningún technician
 * (no existe, o existe con otro rol) devuelve 422 sin modificar la orden
 * (FR-002, SC-002).
 */
class ReassignmentInvalidEmailTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void rejectsAnEmailThatDoesNotExist() throws Exception {
    Order order = createOrder();
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(reassignmentRequest(order.getId(), token, "nobody@fieldops.test"))
        .andExpect(status().isUnprocessableEntity());

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getAssignedTechnician().getId()).isEqualTo(TECHNICIAN_ID);
  }

  @Test
  void rejectsAnEmailThatExistsWithADifferentRole() throws Exception {
    Order order = createOrder();
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(reassignmentRequest(order.getId(), token, "dispatcher@fieldops.test"))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(reassignmentRequest(order.getId(), token, "supervisor@fieldops.test"))
        .andExpect(status().isUnprocessableEntity());

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getAssignedTechnician().getId()).isEqualTo(TECHNICIAN_ID);
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
