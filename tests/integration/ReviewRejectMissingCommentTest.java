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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T036 (US3): rechazar sin {@code comment} (ausente o vacío) devuelve 422 y
 * no modifica el estado de la orden (FR-012a).
 */
class ReviewRejectMissingCommentTest extends BaseIntegrationTest {

  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void rejectingWithoutACommentReturnsUnprocessableEntity() throws Exception {
    Order order = createPendingReviewOrder();
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("decision", "REJECT"))))
        .andExpect(status().isUnprocessableEntity());

    assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.pending_review);
  }

  @Test
  void rejectingWithABlankCommentReturnsUnprocessableEntity() throws Exception {
    Order order = createPendingReviewOrder();
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    Map<String, String> body = new HashMap<>();
    body.put("decision", "REJECT");
    body.put("comment", "   ");

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isUnprocessableEntity());

    assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.pending_review);
  }

  private Order createPendingReviewOrder() {
    return orderRepository.save(
        new Order(
            UUID.randomUUID(),
            OrderStatus.pending_review,
            userRepository.getReferenceById(TECHNICIAN_ID)));
  }
}
