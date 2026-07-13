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
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US2 (FR-006a): una foto adjunta cuyo contenido no es una imagen real
 * (formato no soportado o archivo corrupto) debe rechazarse (422) aunque
 * declare un content-type de imagen, sin marcar la ejecución como
 * registrada.
 *
 * <p>Reutiliza la orden {@code in_progress} sembrada en V2 en vez de
 * insertar una orden nueva, para no alterar el conjunto de IDs que
 * verifican los tests de US1.
 */
class ExecutionInvalidPhotoTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID IN_PROGRESS_ORDER_ID =
      UUID.fromString("a3333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
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
  void registeringExecutionWithACorruptImageIsRejectedAndOrderStatusIsUnchanged() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    MockMultipartFile corruptPhoto =
        new MockMultipartFile(
            "photos",
            "evidence.jpg",
            "image/jpeg",
            "esto no es una imagen, es texto plano".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", IN_PROGRESS_ORDER_ID)
                .file(corruptPhoto)
                .param("note", "nota con foto inválida")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());

    Order persisted = orderRepository.findById(IN_PROGRESS_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.in_progress);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}", IN_PROGRESS_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("in_progress"))
        .andExpect(jsonPath("$.evidencePhotoIds", org.hamcrest.Matchers.hasSize(0)));
  }
}
