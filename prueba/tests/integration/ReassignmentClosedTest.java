import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * US4, escenario de rechazo (FR-014): un dispatcher no puede reasignar una
 * orden en estado {@code closed}; la orden permanece asignada al technician
 * original.
 *
 * <p>Reutiliza la orden {@code closed} sembrada en V2 en vez de insertar
 * órdenes nuevas, para no alterar el conjunto de IDs que verifican los tests
 * de US1.
 */
class ReassignmentClosedTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID ORIGINAL_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID CLOSED_ORDER_ID =
      UUID.fromString("a5555555-5555-5555-5555-555555555555");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetSeedOrder() {
    jdbcTemplate.update(
        "UPDATE orders SET status = 'closed', assigned_technician_id = ? WHERE id = ?",
        ORIGINAL_TECHNICIAN_ID, CLOSED_ORDER_ID);
  }

  @Test
  void reassigningAClosedOrderIsRejectedWithConflict() throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", CLOSED_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newTechnicianId\":\"" + OTHER_TECHNICIAN_ID + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isConflict());

    Order persisted = orderRepository.findById(CLOSED_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.closed);
    assertThat(persisted.getAssignedTechnician().getId()).isEqualTo(ORIGINAL_TECHNICIAN_ID);
  }
}
