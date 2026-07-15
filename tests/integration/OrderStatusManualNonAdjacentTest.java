import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T018 (US2): dispatcher/supervisor intentan una transición que se salta un
 * estado intermedio (no adyacente, ADR-010) -> 409 en cada caso, sin importar
 * la dirección (FR-004, SC-005).
 */
class OrderStatusManualNonAdjacentTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  static Stream<Arguments> nonAdjacentPairs() {
    return Stream.of(
        Arguments.of(OrderStatus.draft, OrderStatus.closed),
        Arguments.of(OrderStatus.draft, OrderStatus.in_progress),
        Arguments.of(OrderStatus.assigned, OrderStatus.pending_review),
        Arguments.of(OrderStatus.assigned, OrderStatus.closed),
        Arguments.of(OrderStatus.in_progress, OrderStatus.closed));
  }

  @ParameterizedTest
  @MethodSource("nonAdjacentPairs")
  void nonAdjacentTransitionIsRejected(OrderStatus origin, OrderStatus target) throws Exception {
    Order order = createOrder(origin);
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", target.name()))))
        .andExpect(status().isConflict());
  }

  private Order createOrder(OrderStatus status) {
    Order order = new Order(UUID.randomUUID(), status, userRepository.getReferenceById(TECHNICIAN_ID));
    return orderRepository.save(order);
  }
}
