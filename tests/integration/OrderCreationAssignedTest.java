import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.security.JwtService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T022 (US3): crear una orden con {@code description} + {@code
 * technicianEmail} válido la deja en {@code assigned}, con
 * {@code lastReassignedBy}/{@code lastReassignedAt} fijados al dispatcher y
 * momento de la creación (FR-007b, FR-007c, SC-004, SC-007).
 */
class OrderCreationAssignedTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void createsAnAssignedOrderWithAuditedAssignment() throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);
    Instant before = Instant.now();

    String response =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "description", "Sustituir batería del UPS",
                                "technicianEmail", "technician@fieldops.test"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("assigned"))
            .andExpect(jsonPath("$.assignedTechnicianEmail").value("technician@fieldops.test"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID createdId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    Order created = orderRepository.findById(createdId).orElseThrow();
    assertThat(created.getStatus()).isEqualTo(OrderStatus.assigned);
    assertThat(created.getAssignedTechnician().getId()).isEqualTo(TECHNICIAN_ID);
    assertThat(created.getLastReassignedBy().getId()).isEqualTo(DISPATCHER_ID);
    assertThat(created.getLastReassignedAt()).isAfterOrEqualTo(before);
  }
}
