import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * T035 (US3): rechazar una orden {@code pending_review} con un comentario no
 * vacío la devuelve a {@code in_progress} y guarda el comentario en
 * {@code rejectionComment} (FR-012, FR-022).
 */
class ReviewRejectTest extends BaseIntegrationTest {

  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String REJECTION_COMMENT =
      "Falta evidencia fotográfica del panel eléctrico completo.";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void rejectingWithACommentReturnsOrderToInProgress() throws Exception {
    Order order =
        orderRepository.save(
            new Order(
                UUID.randomUUID(),
                OrderStatus.pending_review,
                userRepository.getReferenceById(TECHNICIAN_ID)));
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("decision", "REJECT", "comment", REJECTION_COMMENT))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(OrderStatus.in_progress.name()))
        .andExpect(jsonPath("$.rejectionComment").value(REJECTION_COMMENT));

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.in_progress);
    assertThat(reloaded.getRejectionComment()).isEqualTo(REJECTION_COMMENT);
  }
}
