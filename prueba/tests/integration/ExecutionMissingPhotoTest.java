import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US2, escenario 2 (FR-006): registrar la ejecución sin adjuntar ninguna
 * foto de evidencia debe rechazarse (422) y no debe cambiar el estado de la
 * orden.
 *
 * <p>Reutiliza la orden {@code in_progress} sembrada en V2 en vez de
 * insertar una orden nueva, para no alterar el conjunto de IDs que
 * verifican los tests de US1.
 */
class ExecutionMissingPhotoTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID IN_PROGRESS_ORDER_ID =
      UUID.fromString("a3333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetSeedInProgressOrder() {
    jdbcTemplate.update("DELETE FROM evidence_photos WHERE order_id = ?", IN_PROGRESS_ORDER_ID);
    jdbcTemplate.update(
        "UPDATE orders SET status = 'in_progress', execution_note = NULL, rejection_comment = NULL"
            + " WHERE id = ?",
        IN_PROGRESS_ORDER_ID);
  }

  @Test
  void registeringExecutionWithoutAPhotoIsRejectedAndOrderStatusIsUnchanged() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", IN_PROGRESS_ORDER_ID)
                .param("note", "Nota sin evidencia adjunta")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());

    Order persisted = orderRepository.findById(IN_PROGRESS_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.in_progress);
    assertThat(persisted.getExecutionNote()).isNull();
  }
}
