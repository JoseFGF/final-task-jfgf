import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Role;
import com.fieldops.security.JwtService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T016 (US1): un TECHNICIAN ve exactamente las órdenes que tiene asignadas
 * (FR-001), y ninguna otra — ni las de otro technician ni las que no tienen
 * technician asignado (`draft`).
 */
class OrderVisibilityTechnicianTest extends BaseIntegrationTest {

  private static final UUID SEED_TECHNICIAN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  private static final Set<String> EXPECTED_ORDER_IDS_FOR_SEED_TECHNICIAN =
      Set.of(
          "a2222222-2222-2222-2222-222222222222", // assigned
          "a3333333-3333-3333-3333-333333333333", // in_progress
          "a4444444-4444-4444-4444-444444444444"); // pending_review

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void technicianSeesOnlyOwnAssignedOrders() throws Exception {
    String token = jwtService.generateToken(SEED_TECHNICIAN_ID, Role.TECHNICIAN);

    String body =
        mockMvc
            .perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Map<String, Object>> orders =
        objectMapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    Set<String> returnedIds =
        orders.stream().map(order -> (String) order.get("id")).collect(Collectors.toSet());

    assertThat(returnedIds).isEqualTo(EXPECTED_ORDER_IDS_FOR_SEED_TECHNICIAN);
  }
}
