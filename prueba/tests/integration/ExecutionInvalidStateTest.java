import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US2, escenarios 3 y 4 (FR-007): el sistema rechaza el registro de
 * ejecución sobre una orden que no está en {@code in_progress} (409), y
 * sobre una orden asignada a otro technician distinto de quien la solicita
 * (403).
 *
 * <p>Reutiliza órdenes sembradas en V2 en vez de insertar órdenes nuevas,
 * para no alterar el conjunto de IDs que verifican los tests de US1; ninguno
 * de los dos escenarios llega a persistir un cambio (se rechazan antes), por
 * lo que no hace falta restablecer estado entre tests.
 */
class ExecutionInvalidStateTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID ASSIGNED_NOT_IN_PROGRESS_ORDER_ID =
      UUID.fromString("a2222222-2222-2222-2222-222222222222");
  private static final UUID IN_PROGRESS_ORDER_ID =
      UUID.fromString("a3333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetSeedOrders() {
    jdbcTemplate.update(
        "UPDATE orders SET status = 'assigned' WHERE id = ?", ASSIGNED_NOT_IN_PROGRESS_ORDER_ID);
    jdbcTemplate.update(
        "UPDATE orders SET status = 'in_progress' WHERE id = ?", IN_PROGRESS_ORDER_ID);
  }

  @Test
  void registeringExecutionOnAnOrderNotInProgressIsRejectedWithConflict() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", ASSIGNED_NOT_IN_PROGRESS_ORDER_ID)
                .file(validPngPhoto())
                .param("note", "nota")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isConflict());

    Order persisted = orderRepository.findById(ASSIGNED_NOT_IN_PROGRESS_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.assigned);
  }

  @Test
  void aDifferentTechnicianCannotRegisterExecutionOnSomeoneElsesOrder() throws Exception {
    String otherTechnicianToken = jwtService.generateToken(OTHER_TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", IN_PROGRESS_ORDER_ID)
                .file(validPngPhoto())
                .param("note", "nota")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTechnicianToken))
        .andExpect(status().isForbidden());

    Order persisted = orderRepository.findById(IN_PROGRESS_ORDER_ID).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(OrderStatus.in_progress);
  }

  private MockMultipartFile validPngPhoto() throws Exception {
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return new MockMultipartFile("photos", "evidence.png", "image/png", out.toByteArray());
  }
}
