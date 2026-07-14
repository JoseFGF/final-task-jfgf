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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T021 (US3): crear una orden solo con {@code description} la deja en
 * {@code draft}, sin technician asignado (FR-007, SC-004).
 */
class OrderCreationDraftTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void createsADraftOrderWithoutTechnician() throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    String response =
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("description", "Instalar nuevo router en la sala de servidores"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.assignedTechnicianEmail").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.description").value("Instalar nuevo router en la sala de servidores"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID createdId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    List<Order> orders = orderRepository.findAll();
    Order created = orders.stream().filter(o -> o.getId().equals(createdId)).findFirst().orElseThrow();
    assertThat(created.getStatus()).isEqualTo(OrderStatus.draft);
    assertThat(created.getAssignedTechnician()).isNull();
  }
}
