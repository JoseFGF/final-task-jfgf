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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T016 (US2): asignar (no reasignar) un technician a una orden en {@code
 * draft} devuelve 200 y la orden pasa a {@code assigned} (FR-004, FR-005,
 * SC-003).
 */
class ReassignmentDraftInitialAssignmentTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID DRAFT_ORDER_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void assigningATechnicianToADraftOrderMovesItToAssigned() throws Exception {
    Order draftOrder = orderRepository.findById(DRAFT_ORDER_ID).orElseThrow();
    assertThat(draftOrder.getStatus()).isEqualTo(OrderStatus.draft);
    assertThat(draftOrder.getAssignedTechnician()).isNull();

    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/reassignment", DRAFT_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("newTechnicianEmail", "technician@fieldops.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("assigned"))
        .andExpect(jsonPath("$.assignedTechnicianEmail").value("technician@fieldops.test"));

    Order reloaded = orderRepository.findById(DRAFT_ORDER_ID).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.assigned);
    assertThat(reloaded.getAssignedTechnician().getId()).isEqualTo(TECHNICIAN_ID);
  }
}
