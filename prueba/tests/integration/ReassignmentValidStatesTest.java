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
 * US4, escenario principal (FR-013): un dispatcher reasigna una orden en
 * cada uno de los tres estados válidos ({@code assigned}, {@code
 * in_progress}, {@code pending_review}); en {@code pending_review}, la
 * reasignación cambia únicamente el technician, sin alterar el estado ni el
 * comentario de revisión en curso.
 *
 * <p>Reutiliza órdenes sembradas en V2 (en vez de insertar órdenes nuevas)
 * para no alterar el conjunto de IDs que verifican los tests de US1; se
 * restablecen a un estado limpio antes de cada test.
 */
class ReassignmentValidStatesTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID ASSIGNED_ORDER_ID =
      UUID.fromString("a2222222-2222-2222-2222-222222222222");
  private static final UUID IN_PROGRESS_ORDER_ID =
      UUID.fromString("a3333333-3333-3333-3333-333333333333");
  private static final UUID PENDING_REVIEW_ORDER_ID =
      UUID.fromString("a4444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetSeedOrders() {
    jdbcTemplate.update(
        "UPDATE orders SET status = 'assigned', assigned_technician_id = ? WHERE id = ?",
        TECHNICIAN_ID, ASSIGNED_ORDER_ID);
    jdbcTemplate.update(
        "UPDATE orders SET status = 'in_progress', assigned_technician_id = ? WHERE id = ?",
        TECHNICIAN_ID, IN_PROGRESS_ORDER_ID);
    jdbcTemplate.update(
        "UPDATE orders SET status = 'pending_review', assigned_technician_id = ?,"
            + " rejection_comment = 'comentario previo' WHERE id = ?",
        TECHNICIAN_ID, PENDING_REVIEW_ORDER_ID);
  }

  @Test
  void dispatcherReassignsAnAssignedOrder() throws Exception {
    reassignAndExpectOk(ASSIGNED_ORDER_ID);

    Order persisted = orderRepository.findById(ASSIGNED_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.assigned);
    assertThat(persisted.getAssignedTechnician().getId()).isEqualTo(OTHER_TECHNICIAN_ID);
  }

  @Test
  void dispatcherReassignsAnInProgressOrder() throws Exception {
    reassignAndExpectOk(IN_PROGRESS_ORDER_ID);

    Order persisted = orderRepository.findById(IN_PROGRESS_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.in_progress);
    assertThat(persisted.getAssignedTechnician().getId()).isEqualTo(OTHER_TECHNICIAN_ID);
  }

  @Test
  void reassigningAPendingReviewOrderOnlyChangesTechnicianNotState() throws Exception {
    reassignAndExpectOk(PENDING_REVIEW_ORDER_ID);

    Order persisted = orderRepository.findById(PENDING_REVIEW_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.pending_review);
    assertThat(persisted.getAssignedTechnician().getId()).isEqualTo(OTHER_TECHNICIAN_ID);
    assertThat(persisted.getRejectionComment()).isEqualTo("comentario previo");
  }

  private void reassignAndExpectOk(UUID orderId) throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newTechnicianId\":\"" + OTHER_TECHNICIAN_ID + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }
}
