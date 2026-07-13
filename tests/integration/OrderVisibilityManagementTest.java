import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.security.JwtService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T017 (US1): DISPATCHER y SUPERVISOR ven todas las órdenes del sistema, sin
 * importar estado o technician asignado (FR-002, FR-003).
 */
class OrderVisibilityManagementTest extends BaseIntegrationTest {

  private static final UUID SEED_DISPATCHER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SEED_SUPERVISOR_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private OrderRepository orderRepository;

  @Test
  void dispatcherSeesEveryOrderInTheSystem() throws Exception {
    assertRoleSeesEveryOrder(SEED_DISPATCHER_ID, Role.DISPATCHER);
  }

  @Test
  void supervisorSeesEveryOrderInTheSystem() throws Exception {
    assertRoleSeesEveryOrder(SEED_SUPERVISOR_ID, Role.SUPERVISOR);
  }

  private void assertRoleSeesEveryOrder(UUID userId, Role role) throws Exception {
    String token = jwtService.generateToken(userId, role);
    long totalOrdersInDatabase = orderRepository.count();

    String body =
        mockMvc
            .perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Map<String, Object>> orders = objectMapper.readValue(body, new TypeReference<>() {});

    assertThat(orders).hasSize((int) totalOrdersInDatabase);
  }
}
