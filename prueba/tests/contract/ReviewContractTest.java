import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prueba.model.Role;
import com.prueba.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test de {@code POST /orders/{orderId}/review} contra
 * prueba/contracts/openapi.yaml (US3: FR-009 a FR-012a): 200 con el schema
 * {@code OrderDetail}, 401 sin sesión, 403 si quien la solicita no es
 * SUPERVISOR, 404 si la orden no existe, 409 si no está en
 * {@code pending_review}, 422 si se rechaza sin comentario.
 *
 * <p>Reutiliza la orden {@code pending_review} sembrada en V2 (en vez de
 * insertar órdenes nuevas) para no alterar el conjunto de IDs que verifican
 * los tests de US1; cada test la restablece a un estado limpio antes de
 * actuar.
 */
class ReviewContractTest extends BaseIntegrationTest {

  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID PENDING_REVIEW_ORDER_ID =
      UUID.fromString("a4444444-4444-4444-4444-444444444444");
  private static final UUID ASSIGNED_ORDER_ID =
      UUID.fromString("a2222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetSeedOrders() {
    jdbcTemplate.update(
        "UPDATE orders SET status = 'pending_review', rejection_comment = NULL WHERE id = ?",
        PENDING_REVIEW_ORDER_ID);
    jdbcTemplate.update("UPDATE orders SET status = 'assigned' WHERE id = ?", ASSIGNED_ORDER_ID);
  }

  @Test
  void approveReturnsOrderDetailSchemaAndClosesOrder() throws Exception {
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", PENDING_REVIEW_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(PENDING_REVIEW_ORDER_ID.toString()))
        .andExpect(jsonPath("$.status").value("closed"));
  }

  @Test
  void reviewWithoutSessionIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", PENDING_REVIEW_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void reviewByNonSupervisorIsForbidden() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", PENDING_REVIEW_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void reviewOnUnknownOrderReturnsNotFound() throws Exception {
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void reviewOnOrderNotPendingReviewIsConflict() throws Exception {
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", ASSIGNED_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectWithoutCommentIsUnprocessable() throws Exception {
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/review", PENDING_REVIEW_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());
  }
}
