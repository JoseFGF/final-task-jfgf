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
 * T033 (US3): contract test de {@code POST /orders/{orderId}/review} contra
 * contracts/openapi.yaml — 200, 401, 403, 404, 409, 422 (FR-009 a FR-012a).
 */
class ReviewContractTest extends BaseIntegrationTest {

  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void approvingAPendingReviewOrderReturnsOk() throws Exception {
    Order order = createOrder(OrderStatus.pending_review);
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(reviewRequest(order.getId(), token, Map.of("decision", "APPROVE")))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsRequestWithoutSession() throws Exception {
    Order order = createOrder(OrderStatus.pending_review);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", order.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsRoleOtherThanSupervisor() throws Exception {
    Order order = createOrder(OrderStatus.pending_review);
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(reviewRequest(order.getId(), token, Map.of("decision", "APPROVE")))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsNotFoundForAnUnknownOrder() throws Exception {
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(reviewRequest(UUID.randomUUID(), token, Map.of("decision", "APPROVE")))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsReviewWhenOrderIsNotPendingReview() throws Exception {
    Order order = createOrder(OrderStatus.in_progress);
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(reviewRequest(order.getId(), token, Map.of("decision", "APPROVE")))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsRejectionWithoutComment() throws Exception {
    Order order = createOrder(OrderStatus.pending_review);
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(reviewRequest(order.getId(), token, Map.of("decision", "REJECT")))
        .andExpect(status().isUnprocessableEntity());
  }

  private MockHttpServletRequestBuilder reviewRequest(
      UUID orderId, String token, Map<String, String> body) throws Exception {
    return post("/api/v1/orders/{orderId}/review", orderId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private Order createOrder(OrderStatus status) {
    Order order = new Order(UUID.randomUUID(), status, userRepository.getReferenceById(TECHNICIAN_ID));
    return orderRepository.save(order);
  }
}
