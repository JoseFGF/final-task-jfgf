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
 * T025 (US2): registrar una ejecución válida (nota + al menos una foto)
 * sobre una orden {@code in_progress} la pasa a {@code pending_review},
 * guardando la nota y la evidencia (FR-005, FR-008, acceptance scenario 1).
 */
class ExecutionRegisterTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN2_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void validExecutionMovesOrderToPendingReviewAndPersistsNoteAndEvidence() throws Exception {
    Order order =
        orderRepository.save(
            new Order(
                UUID.randomUUID(),
                OrderStatus.in_progress,
                userRepository.getReferenceById(TECHNICIAN2_ID)));
    String token = jwtService.generateToken(TECHNICIAN2_ID, Role.TECHNICIAN);
    String note = "Se reemplazó el componente dañado; equipo operativo de nuevo.";

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", order.getId())
                .file(new MockMultipartFile("photos", "evidence.png", "image/png", TestImages.validPng()))
                .param("note", note)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(OrderStatus.pending_review.name()))
        .andExpect(jsonPath("$.executionNote").value(note))
        .andExpect(jsonPath("$.evidencePhotoIds", org.hamcrest.Matchers.hasSize(1)));

    // La evidencia (evidencePhotoIds) ya se verifica arriba en la respuesta HTTP;
    // el estado y la nota se re-verifican directamente en base de datos.
    Order persisted = orderRepository.findById(order.getId()).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.pending_review);
    assertThat(persisted.getExecutionNote()).isEqualTo(note);
  }
}
