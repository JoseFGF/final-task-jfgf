import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prueba.model.Order;
import com.prueba.model.OrderStatus;
import com.prueba.model.Role;
import com.prueba.repository.OrderRepository;
import com.prueba.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US3, escenario 2 (FR-009, FR-012): un supervisor rechaza una orden en
 * {@code pending_review} adjuntando un comentario, y esta vuelve a
 * {@code in_progress} conservando el comentario de rechazo.
 *
 * <p>Reutiliza la orden {@code pending_review} sembrada en V2 (en vez de
 * insertar órdenes nuevas) para no alterar el conjunto de IDs que verifican
 * los tests de US1; se restablece a un estado limpio antes de actuar.
 */
class ReviewRejectTest extends BaseIntegrationTest {

  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PENDING_REVIEW_ORDER_ID =
      UUID.fromString("a4444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetSeedOrder() {
    jdbcTemplate.update(
        "UPDATE orders SET status = 'pending_review', rejection_comment = NULL WHERE id = ?",
        PENDING_REVIEW_ORDER_ID);
  }

  @Test
  void supervisorRejectsOrderWithCommentAndItReturnsToInProgress() throws Exception {
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", PENDING_REVIEW_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"decision\":\"REJECT\",\"comment\":\"Falta evidencia de la zona afectada\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("in_progress"))
        .andExpect(jsonPath("$.rejectionComment").value("Falta evidencia de la zona afectada"));

    Order persisted = orderRepository.findById(PENDING_REVIEW_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.in_progress);
    assertThat(persisted.getRejectionComment()).isEqualTo("Falta evidencia de la zona afectada");
  }
}
