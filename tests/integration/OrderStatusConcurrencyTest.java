import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.JwtService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * T021 (US2): dos cambios de estado concurrentes reales (no simulados) sobre
 * la misma orden, ambos pidiendo la misma transición ({@code assigned ->
 * in_progress}, FR-008). A diferencia de la reasignación (idempotente sobre
 * el technician destino), esta transición deja de ser válida para el
 * "perdedor" en cuanto el "ganador" la aplica (la orden ya no está en
 * {@code assigned}): el bloqueo optimista con reintento (ADR-011) hace que
 * ninguna de las dos peticiones propague un 500, pero solo una de ellas
 * termina siendo una transición válida — la otra recibe un 409 controlado
 * (GlobalExceptionHandler), no una excepción no controlada. La orden termina
 * en exactamente el estado pedido, nunca en uno contradictorio.
 */
class OrderStatusConcurrencyTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void concurrentStatusChangesNeverCrashAndExactlyOneWins() throws Exception {
    Order order =
        orderRepository.save(
            new Order(
                UUID.randomUUID(),
                OrderStatus.assigned,
                userRepository.getReferenceById(TECHNICIAN_ID)));
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    CountDownLatch startGate = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> first =
          executor.submit(() -> changeStatus(order.getId(), token, "in_progress", startGate));
      Future<Integer> second =
          executor.submit(() -> changeStatus(order.getId(), token, "in_progress", startGate));

      startGate.countDown();

      int firstStatus = first.get(10, TimeUnit.SECONDS);
      int secondStatus = second.get(10, TimeUnit.SECONDS);

      // Ninguna de las dos peticiones debe propagar un error no controlado
      // (500): o gana la transición (200) o llega tarde y la orden ya no
      // está en el estado de origen esperado (409, GlobalExceptionHandler).
      assertThat(List.of(firstStatus, secondStatus))
          .containsExactlyInAnyOrder(HttpStatus.OK.value(), HttpStatus.CONFLICT.value());
    } finally {
      executor.shutdownNow();
    }

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.in_progress);
  }

  private int changeStatus(UUID orderId, String token, String targetStatus, CountDownLatch startGate)
      throws Exception {
    startGate.await();
    return mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/v1/orders/{orderId}/status", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", targetStatus))))
        .andReturn()
        .getResponse()
        .getStatus();
  }
}
