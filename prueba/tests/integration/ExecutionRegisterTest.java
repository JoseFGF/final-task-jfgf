import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prueba.model.Order;
import com.prueba.model.OrderStatus;
import com.prueba.model.Role;
import com.prueba.repository.OrderRepository;
import com.prueba.security.JwtService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US2, escenarios 1 y 5 (FR-005, FR-008, FR-022): un technician registra la
 * ejecución de una orden propia en {@code in_progress} y esta pasa a
 * {@code pending_review} con la nota y la evidencia guardadas; si la orden
 * volvía de un rechazo, el {@code rejectionComment} previo se conserva tras
 * el nuevo registro.
 *
 * <p>Reutiliza la orden {@code in_progress} sembrada en V2 (en vez de
 * insertar órdenes nuevas) para no alterar el conjunto de IDs que verifican
 * los tests de US1; cada test la restablece a un estado limpio antes de
 * actuar.
 */
class ExecutionRegisterTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID IN_PROGRESS_ORDER_ID =
      UUID.fromString("a3333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void technicianRegistersExecutionAndOrderMovesToPendingReview() throws Exception {
    resetSeedOrder(null);
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", IN_PROGRESS_ORDER_ID)
                .file(validPngPhoto())
                .param("note", "Se revisó el equipo y quedó operativo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending_review"))
        .andExpect(jsonPath("$.executionNote").value("Se revisó el equipo y quedó operativo"))
        .andExpect(jsonPath("$.evidencePhotoIds", org.hamcrest.Matchers.hasSize(1)));

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", IN_PROGRESS_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending_review"))
        .andExpect(jsonPath("$.executionNote").value("Se revisó el equipo y quedó operativo"))
        .andExpect(jsonPath("$.evidencePhotoIds", org.hamcrest.Matchers.hasSize(1)));

    Order persisted = orderRepository.findById(IN_PROGRESS_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.pending_review);
    assertThat(persisted.getExecutionNote()).isEqualTo("Se revisó el equipo y quedó operativo");
  }

  @Test
  void reRegisteringExecutionAfterRejectionKeepsRejectionCommentAndReturnsToPendingReview()
      throws Exception {
    resetSeedOrder("Falta evidencia de la zona afectada, corregir.");
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", IN_PROGRESS_ORDER_ID)
                .file(validPngPhoto())
                .param("note", "Nota corregida con la evidencia solicitada")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());

    Order persisted = orderRepository.findById(IN_PROGRESS_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.pending_review);
    assertThat(persisted.getRejectionComment())
        .isEqualTo("Falta evidencia de la zona afectada, corregir.");
  }

  /** Restablece la orden {@code in_progress} sembrada a un estado limpio y conocido. */
  private void resetSeedOrder(String rejectionComment) {
    jdbcTemplate.update("DELETE FROM evidence_photos WHERE order_id = ?", IN_PROGRESS_ORDER_ID);
    jdbcTemplate.update(
        "UPDATE orders SET status = 'in_progress', execution_note = NULL, rejection_comment = ?"
            + " WHERE id = ?",
        rejectionComment,
        IN_PROGRESS_ORDER_ID);
  }

  private MockMultipartFile validPngPhoto() throws Exception {
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return new MockMultipartFile("photos", "evidence.png", "image/png", out.toByteArray());
  }
}
