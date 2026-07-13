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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T040 (US4): contract test de {@code POST /orders/{orderId}/reassignment}
 * contra contracts/openapi.yaml — 200, 401, 403, 404, 409 (FR-013, FR-014).
 */
class ReassignmentContractTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void reassigningAnAssignedOrderReturnsOk() throws Exception {
    Order order = createOrder(OrderStatus.assigned);
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(reassignmentRequest(order.getId(), token, OTHER_TECHNICIAN_ID))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsRequestWithoutSession() throws Exception {
    Order order = createOrder(OrderStatus.assigned);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", order.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("newTechnicianId", OTHER_TECHNICIAN_ID.toString()))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsRoleOtherThanDispatcher() throws Exception {
    Order order = createOrder(OrderStatus.assigned);
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(reassignmentRequest(order.getId(), token, OTHER_TECHNICIAN_ID))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsNotFoundForAnUnknownOrder() throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(reassignmentRequest(UUID.randomUUID(), token, OTHER_TECHNICIAN_ID))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsReassignmentOfAClosedOrder() throws Exception {
    Order order = createOrder(OrderStatus.closed);
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(reassignmentRequest(order.getId(), token, OTHER_TECHNICIAN_ID))
        .andExpect(status().isConflict());
  }

  private MockHttpServletRequestBuilder reassignmentRequest(
      UUID orderId, String token, UUID newTechnicianId) throws Exception {
    return post("/api/v1/orders/{orderId}/reassignment", orderId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            objectMapper.writeValueAsString(Map.of("newTechnicianId", newTechnicianId.toString())));
  }

  private Order createOrder(OrderStatus status) {
    Order order = new Order(UUID.randomUUID(), status, userRepository.getReferenceById(TECHNICIAN_ID));
    return orderRepository.save(order);
  }
}
