import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * (US2, FR-005) `note` es obligatoria y no puede estar en blanco al registrar
 * la ejecución de una orden: tanto si el parámetro falta por completo (lo que
 * Spring resuelve como {@code MissingServletRequestParameterException}) como
 * si llega en blanco, la API responde 422 con el schema {@code ErrorResponse}
 * de contracts/openapi.yaml, y la orden no cambia de estado.
 */
class ExecutionBlankNoteTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN2_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void blankNoteIsRejectedAndOrderStatusIsUnchanged() throws Exception {
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
                .file(new MockMultipartFile("photos", "evidence.png", "image/png", TestImages.validPng()))
                .param("note", "   ")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    Order persisted = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.in_progress);
    assertThat(persisted.getExecutionNote()).isNull();
  }

  @Test
  void missingNoteParameterIsRejectedWithErrorResponseSchema() throws Exception {
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
                .file(new MockMultipartFile("photos", "evidence.png", "image/png", TestImages.validPng()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").exists());

    Order persisted = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.in_progress);
  }
}
