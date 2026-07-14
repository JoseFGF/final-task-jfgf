import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.JwtService;
import java.util.Map;
import java.util.Set;
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
 * T043 (US4): dos reasignaciones concurrentes sobre la misma orden deben
 * responder ambas 200 (el bloqueo optimista con reintento evita exponer un
 * error de concurrencia al cliente), y la orden debe terminar asignada a
 * exactamente uno de los dos technicians solicitados, nunca en un estado
 * contradictorio (FR-021).
 */
class ReassignmentConcurrencyTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final String TECHNICIAN_EMAIL = "technician@fieldops.test";
  private static final String OTHER_TECHNICIAN_EMAIL = "technician2@fieldops.test";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void concurrentReassignmentsBothSucceedAndOneTechnicianWins() throws Exception {
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
          executor.submit(() -> reassign(order.getId(), token, TECHNICIAN_EMAIL, startGate));
      Future<Integer> second =
          executor.submit(() -> reassign(order.getId(), token, OTHER_TECHNICIAN_EMAIL, startGate));

      startGate.countDown();

      int firstStatus = first.get(10, TimeUnit.SECONDS);
      int secondStatus = second.get(10, TimeUnit.SECONDS);

      assertThat(firstStatus).isEqualTo(HttpStatus.OK.value());
      assertThat(secondStatus).isEqualTo(HttpStatus.OK.value());
    } finally {
      executor.shutdownNow();
    }

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getAssignedTechnician().getId())
        .isIn(Set.of(TECHNICIAN_ID, OTHER_TECHNICIAN_ID));
    assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.assigned);
  }

  private int reassign(UUID orderId, String token, String newTechnicianEmail, CountDownLatch startGate)
      throws Exception {
    startGate.await();
    return mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/v1/orders/{orderId}/reassignment", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("newTechnicianEmail", newTechnicianEmail))))
        .andReturn()
        .getResponse()
        .getStatus();
  }
}
