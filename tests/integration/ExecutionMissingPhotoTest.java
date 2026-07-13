import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
 * T026 (US2): registrar la ejecución sin adjuntar ninguna foto se rechaza
 * (422) y la orden no cambia de estado (FR-006, acceptance scenario 2).
 */
class ExecutionMissingPhotoTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN2_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void registrationWithoutAnyPhotoIsRejectedAndOrderStatusIsUnchanged() throws Exception {
    Order order =
        orderRepository.save(
            new Order(
                UUID.randomUUID(),
                OrderStatus.in_progress,
                userRepository.getReferenceById(TECHNICIAN2_ID)));
    String token = jwtService.generateToken(TECHNICIAN2_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", order.getId())
                .param("note", "trabajo completado, sin evidencia adjunta")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());

    Order persisted = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.in_progress);
    assertThat(persisted.getExecutionNote()).isNull();
  }
}
