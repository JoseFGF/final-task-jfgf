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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T027 (US2): rechaza el registro de ejecución cuando la orden no está en
 * {@code in_progress} (409, acceptance scenario 3) o cuando no está asignada
 * al technician que la solicita (403, acceptance scenario 4) — FR-007.
 */
class ExecutionInvalidStateTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN1_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID TECHNICIAN2_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void ordersNotInProgressAreRejectedWithConflict() throws Exception {
    Order draftOrder =
        orderRepository.save(
            new Order(
                UUID.randomUUID(),
                OrderStatus.assigned,
                userRepository.getReferenceById(TECHNICIAN2_ID)));
    String token = jwtService.generateToken(TECHNICIAN2_ID, Role.TECHNICIAN);

    mockMvc
        .perform(executionRequest(draftOrder.getId(), token))
        .andExpect(status().isConflict());

    assertThat(orderRepository.findById(draftOrder.getId()).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.assigned);
  }

  @Test
  void ordersNotAssignedToTheRequestingTechnicianAreRejectedWithForbidden() throws Exception {
    Order orderOfAnotherTechnician =
        orderRepository.save(
            new Order(
                UUID.randomUUID(),
                OrderStatus.in_progress,
                userRepository.getReferenceById(TECHNICIAN2_ID)));
    String token = jwtService.generateToken(TECHNICIAN1_ID, Role.TECHNICIAN);

    mockMvc
        .perform(executionRequest(orderOfAnotherTechnician.getId(), token))
        .andExpect(status().isForbidden());

    assertThat(orderRepository.findById(orderOfAnotherTechnician.getId()).orElseThrow().getStatus())
        .isEqualTo(OrderStatus.in_progress);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      executionRequest(UUID orderId, String token) {
    return multipart("/api/v1/orders/{orderId}/execution", orderId)
        .file(new MockMultipartFile("photos", "evidence.png", "image/png", TestImages.validPng()))
        .param("note", "intento de registro")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }
}
