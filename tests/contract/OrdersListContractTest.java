import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fieldops.model.Role;
import com.fieldops.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T014 (US1): contract test de {@code GET /orders} contra
 * contracts/openapi.yaml — 200 con sesión válida, 401 sin ella (FR-001 a
 * FR-003, FR-017).
 */
class OrdersListContractTest extends BaseIntegrationTest {

  private static final UUID SEED_DISPATCHER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String ORDERS_PATH = "/api/v1/orders";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;

  @Test
  void returnsOrdersForAnAuthenticatedUser() throws Exception {
    String token = jwtService.generateToken(SEED_DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(get(ORDERS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  void rejectsRequestWithoutSession() throws Exception {
    mockMvc.perform(get(ORDERS_PATH)).andExpect(status().isUnauthorized());
  }
}
