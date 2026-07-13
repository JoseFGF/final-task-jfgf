import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T027b (US2): {@code GET /orders/{orderId}} devuelve el {@code
 * rejectionComment} correcto de una orden que volvió a {@code in_progress}
 * tras un rechazo del supervisor (FR-022, acceptance scenario 5). La orden
 * se siembra directamente con el comentario ya seteado: el endpoint de
 * rechazo (US3) no es parte de esta fase.
 */
class ExecutionRejectionCommentVisibilityTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN2_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final String REJECTION_COMMENT =
      "Falta una foto del panel eléctrico completo; adjuntar de nuevo con mejor iluminación.";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void technicianSeesTheRejectionCommentBeforeCorrectingTheExecution() throws Exception {
    Order order =
        new Order(
            UUID.randomUUID(), OrderStatus.in_progress, userRepository.getReferenceById(TECHNICIAN2_ID));
    order.setExecutionNote("Nota original antes del rechazo del supervisor.");
    order.setRejectionComment(REJECTION_COMMENT);
    orderRepository.save(order);

    String token = jwtService.generateToken(TECHNICIAN2_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rejectionComment").value(REJECTION_COMMENT))
        .andExpect(jsonPath("$.status").value(OrderStatus.in_progress.name()));
  }
}
