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
 * Contract test de {@code POST /orders/{orderId}/reassignment} contra
 * prueba/contracts/openapi.yaml (US4: FR-013, FR-014, FR-021): 200 con el
 * schema {@code OrderDetail}, 401 sin sesión, 403 si quien la solicita no es
 * DISPATCHER, 404 si la orden no existe, 409 si está en {@code closed}.
 *
 * <p>Reutiliza órdenes sembradas en V2 (en vez de insertar órdenes nuevas)
 * para no alterar el conjunto de IDs que verifican los tests de US1; cada
 * test restablece el estado que necesita antes de actuar.
 */
class ReassignmentContractTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID ASSIGNED_ORDER_ID =
      UUID.fromString("a2222222-2222-2222-2222-222222222222");
  private static final UUID CLOSED_ORDER_ID =
      UUID.fromString("a5555555-5555-5555-5555-555555555555");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetSeedOrders() {
    jdbcTemplate.update(
        "UPDATE orders SET status = 'assigned', assigned_technician_id = ? WHERE id = ?",
        TECHNICIAN_ID, ASSIGNED_ORDER_ID);
    jdbcTemplate.update("UPDATE orders SET status = 'closed' WHERE id = ?", CLOSED_ORDER_ID);
  }

  @Test
  void reassignmentReturnsOrderDetailSchemaWithNewTechnician() throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", ASSIGNED_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newTechnicianId\":\"" + OTHER_TECHNICIAN_ID + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ASSIGNED_ORDER_ID.toString()))
        .andExpect(jsonPath("$.assignedTechnicianId").value(OTHER_TECHNICIAN_ID.toString()))
        .andExpect(jsonPath("$.status").value("assigned"));
  }

  @Test
  void reassignmentWithoutSessionIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", ASSIGNED_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newTechnicianId\":\"" + OTHER_TECHNICIAN_ID + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void reassignmentByNonDispatcherIsForbidden() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", ASSIGNED_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newTechnicianId\":\"" + OTHER_TECHNICIAN_ID + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void reassignmentOnUnknownOrderReturnsNotFound() throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newTechnicianId\":\"" + OTHER_TECHNICIAN_ID + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void reassignmentOnClosedOrderIsConflict() throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", CLOSED_ORDER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newTechnicianId\":\"" + OTHER_TECHNICIAN_ID + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }
}
