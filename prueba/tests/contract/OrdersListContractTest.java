import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prueba.model.Role;
import com.prueba.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test de {@code GET /orders} contra prueba/contracts/openapi.yaml
 * (FR-001, FR-002, FR-003): 200 con un array de {@code OrderSummary} para
 * cualquier rol autenticado, 401 sin sesión válida (FR-017).
 */
class OrdersListContractTest extends BaseIntegrationTest {

  private static final String ORDERS_PATH = "/api/v1/orders";
  private static final UUID SEED_DISPATCHER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;

  @Test
  void listOrdersReturnsSummarySchemaForAnAuthenticatedRole() throws Exception {
    String token = jwtService.generateToken(SEED_DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(get(ORDERS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].status").exists())
        .andExpect(jsonPath("$[0].createdAt").exists())
        .andExpect(jsonPath("$[0].updatedAt").exists());
  }

  @Test
  void listOrdersWithoutSessionIsRejected() throws Exception {
    mockMvc.perform(get(ORDERS_PATH)).andExpect(status().isUnauthorized());
  }
}
