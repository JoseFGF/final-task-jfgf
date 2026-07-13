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
 * US1, escenario 1 / FR-001: un TECHNICIAN debe ver exactamente las órdenes
 * que tiene asignadas, en cualquier estado, y ninguna otra — ni las de otro
 * technician ni las que aún no tienen technician asignado — usando los
 * datos sembrados en V2__seed_data.sql.
 */
class OrderVisibilityTechnicianTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final List<UUID> TECHNICIAN_OWN_ORDER_IDS =
      List.of(
          UUID.fromString("a2222222-2222-2222-2222-222222222222"), // assigned
          UUID.fromString("a3333333-3333-3333-3333-333333333333"), // in_progress
          UUID.fromString("a4444444-4444-4444-4444-444444444444")); // pending_review
  private static final UUID OTHER_TECHNICIAN_ORDER_ID =
      UUID.fromString("a5555555-5555-5555-5555-555555555555"); // closed, technician2
  private static final UUID UNASSIGNED_DRAFT_ORDER_ID =
      UUID.fromString("a1111111-1111-1111-1111-111111111111"); // draft, sin technician

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void technicianSeesOnlyTheirOwnAssignedOrders() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    String body =
        mockMvc
            .perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<UUID> visibleIds = extractOrderIds(body);

    assertThat(visibleIds).containsExactlyInAnyOrderElementsOf(TECHNICIAN_OWN_ORDER_IDS);
    assertThat(visibleIds).doesNotContain(OTHER_TECHNICIAN_ORDER_ID, UNASSIGNED_DRAFT_ORDER_ID);
  }

  private List<UUID> extractOrderIds(String jsonArrayBody) throws Exception {
    JsonNode root = objectMapper.readTree(jsonArrayBody);
    return StreamSupport.stream(root.spliterator(), false)
        .map(node -> UUID.fromString(node.get("id").asText()))
        .toList();
  }
}
