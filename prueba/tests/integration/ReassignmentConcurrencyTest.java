import static org.assertj.core.api.Assertions.assertThat;

import com.prueba.model.Order;
import com.prueba.model.Role;
import com.prueba.repository.OrderRepository;
import com.prueba.security.JwtService;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * US4, escenario de concurrencia (FR-021): dos dispatchers reasignan la
 * misma orden a technicians distintos al mismo tiempo. El bloqueo optimista
 * de {@code version} con reintento automático del servicio garantiza que
 * ambas peticiones reciban 200 (nunca un error de concurrencia) y que la
 * orden termine asignada a exactamente uno de los dos technicians, nunca a
 * ambos ni sin asignar.
 *
 * <p>Reutiliza la orden {@code assigned} sembrada en V2 en vez de insertar
 * una orden nueva, para no alterar el conjunto de IDs que verifican los
 * tests de US1.
 */
class ReassignmentConcurrencyTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_A_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID TECHNICIAN_B_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID ORDER_ID = UUID.fromString("a2222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetSeedOrder() {
    jdbcTemplate.update(
        "UPDATE orders SET status = 'assigned', assigned_technician_id = ? WHERE id = ?",
        TECHNICIAN_A_ID, ORDER_ID);
  }

  @Test
  void concurrentReassignmentsBothSucceedAndOrderEndsWithExactlyOneTechnician() throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);
    CountDownLatch startGate = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Callable<MvcResult> reassignToA = reassignmentCall(token, TECHNICIAN_A_ID, startGate);
      Callable<MvcResult> reassignToB = reassignmentCall(token, TECHNICIAN_B_ID, startGate);

      Future<MvcResult> futureA = executor.submit(reassignToA);
      Future<MvcResult> futureB = executor.submit(reassignToB);

      startGate.countDown();

      MvcResult resultA = futureA.get(30, TimeUnit.SECONDS);
      MvcResult resultB = futureB.get(30, TimeUnit.SECONDS);

      assertThat(resultA.getResponse().getStatus()).isEqualTo(200);
      assertThat(resultB.getResponse().getStatus()).isEqualTo(200);
    } finally {
      executor.shutdown();
    }

    Order persisted = orderRepository.findById(ORDER_ID).orElseThrow();
    assertThat(persisted.getAssignedTechnician()).isNotNull();
    assertThat(persisted.getAssignedTechnician().getId())
        .isIn(Set.of(TECHNICIAN_A_ID, TECHNICIAN_B_ID));
  }

  private Callable<MvcResult> reassignmentCall(
      String token, UUID newTechnicianId, CountDownLatch startGate) {
    return () -> {
      startGate.await();
      return mockMvc
          .perform(
              MockMvcRequestBuilders.post("/api/v1/orders/{orderId}/reassignment", ORDER_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"newTechnicianId\":\"" + newTechnicianId + "\"}")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andReturn();
    };
  }
}
