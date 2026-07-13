import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.model.Role;
import com.prueba.security.JwtService;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US1, escenarios 2 y 3 / FR-002, FR-003: DISPATCHER y SUPERVISOR ven TODAS
 * las órdenes del sistema, sin importar su estado ni a qué technician estén
 * asignadas — incluidas las que están en {@code pending_review}, que el
 * supervisor necesita distinguir como pendientes de su revisión.
 */
class OrderVisibilityManagementTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SUPERVISOR_ID =
      UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PENDING_REVIEW_ORDER_ID =
      UUID.fromString("a4444444-4444-4444-4444-444444444444");
  private static final List<UUID> ALL_SEED_ORDER_IDS =
      List.of(
          UUID.fromString("a1111111-1111-1111-1111-111111111111"),
          UUID.fromString("a2222222-2222-2222-2222-222222222222"),
          UUID.fromString("a3333333-3333-3333-3333-333333333333"),
          PENDING_REVIEW_ORDER_ID,
          UUID.fromString("a5555555-5555-5555-5555-555555555555"));

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void dispatcherSeesAllOrdersRegardlessOfAssignedTechnicianOrStatus() throws Exception {
    List<UUID> visibleIds = fetchVisibleOrderIds(DISPATCHER_ID, Role.DISPATCHER);
    assertThat(visibleIds).containsExactlyInAnyOrderElementsOf(ALL_SEED_ORDER_IDS);
  }

  @Test
  void supervisorSeesAllOrdersIncludingThosePendingReview() throws Exception {
    List<UUID> visibleIds = fetchVisibleOrderIds(SUPERVISOR_ID, Role.SUPERVISOR);
    assertThat(visibleIds).containsExactlyInAnyOrderElementsOf(ALL_SEED_ORDER_IDS);
    assertThat(visibleIds).contains(PENDING_REVIEW_ORDER_ID);
  }

  private List<UUID> fetchVisibleOrderIds(UUID userId, Role role) throws Exception {
    String token = jwtService.generateToken(userId, role);
    String body =
        mockMvc
            .perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode root = objectMapper.readTree(body);
    return StreamSupport.stream(root.spliterator(), false)
        .map(node -> UUID.fromString(node.get("id").asText()))
        .toList();
  }
}
