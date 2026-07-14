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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T041 (US4): reasignar una orden en {@code assigned}, {@code in_progress} o
 * {@code pending_review} devuelve 200 y cambia el technician asignado
 * (FR-013). Reasignar una orden en {@code pending_review} no debe alterar su
 * estado ni el resultado de la revisión en curso.
 */
class ReassignmentValidStatesTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final String OTHER_TECHNICIAN_EMAIL = "technician2@fieldops.test";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  static Stream<OrderStatus> reassignableStatuses() {
    return Stream.of(OrderStatus.assigned, OrderStatus.in_progress, OrderStatus.pending_review);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"assigned", "in_progress", "pending_review"})
  void reassigningInAValidStateReturnsOkAndChangesTechnician(OrderStatus status) throws Exception {
    Order order = createOrder(status);
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("newTechnicianEmail", OTHER_TECHNICIAN_EMAIL))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignedTechnicianEmail").value(OTHER_TECHNICIAN_EMAIL))
        .andExpect(jsonPath("$.status").value(status.name()));

    Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(reloaded.getAssignedTechnician().getId()).isEqualTo(OTHER_TECHNICIAN_ID);
    assertThat(reloaded.getStatus()).isEqualTo(status);
  }

  private Order createOrder(OrderStatus status) {
    Order order = new Order(UUID.randomUUID(), status, userRepository.getReferenceById(TECHNICIAN_ID));
    return orderRepository.save(order);
  }
}
