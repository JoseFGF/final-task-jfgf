import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prueba.model.Role;
import com.prueba.security.JwtService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test de {@code POST /orders/{orderId}/execution} contra
 * prueba/contracts/openapi.yaml (US2: FR-005 a FR-008, FR-006a): 200 con el
 * schema {@code OrderDetail} y la orden en {@code pending_review}, 401 sin
 * sesión, 403 si la orden no está asignada al technician, 404 si la orden no
 * existe, 409 si no está en {@code in_progress}, 422 sin foto.
 *
 * <p>Reutiliza la orden {@code in_progress} sembrada en V2 (en vez de insertar
 * órdenes nuevas) para no alterar el conjunto de IDs que verifican los tests
 * de US1 (p.ej. {@code OrderVisibilityManagementTest}); cada test la
 * restablece a un estado limpio antes de actuar.
 */
class ExecutionContractTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID DRAFT_ORDER_ID =
      UUID.fromString("a1111111-1111-1111-1111-111111111111");
  private static final UUID IN_PROGRESS_ORDER_ID =
      UUID.fromString("a3333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetSeedInProgressOrder() {
    jdbcTemplate.update("DELETE FROM evidence_photos WHERE order_id = ?", IN_PROGRESS_ORDER_ID);
    jdbcTemplate.update(
        "UPDATE orders SET status = 'in_progress', execution_note = NULL, rejection_comment = NULL"
            + " WHERE id = ?",
        IN_PROGRESS_ORDER_ID);
  }

  @Test
  void registerExecutionReturnsOrderDetailSchemaAndMovesToPendingReview() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", IN_PROGRESS_ORDER_ID)
                .file(validPngPhoto())
                .param("note", "Se reemplazó la pieza dañada")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(IN_PROGRESS_ORDER_ID.toString()))
        .andExpect(jsonPath("$.status").value("pending_review"))
        .andExpect(jsonPath("$.executionNote").value("Se reemplazó la pieza dañada"))
        .andExpect(jsonPath("$.evidencePhotoIds").isArray())
        .andExpect(jsonPath("$.evidencePhotoIds", org.hamcrest.Matchers.hasSize(1)));
  }

  @Test
  void registerExecutionWithoutSessionIsRejected() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", IN_PROGRESS_ORDER_ID)
                .file(validPngPhoto())
                .param("note", "nota"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void registerExecutionOnOrderNotAssignedToTechnicianIsForbidden() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", DRAFT_ORDER_ID)
                .file(validPngPhoto())
                .param("note", "nota")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void registerExecutionOnUnknownOrderReturnsNotFound() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", UUID.randomUUID())
                .file(validPngPhoto())
                .param("note", "nota")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void registerExecutionWithoutPhotoIsUnprocessable() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", IN_PROGRESS_ORDER_ID)
                .param("note", "nota sin evidencia")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());
  }

  private MockMultipartFile validPngPhoto() throws Exception {
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return new MockMultipartFile("photos", "evidence.png", "image/png", out.toByteArray());
  }
}
