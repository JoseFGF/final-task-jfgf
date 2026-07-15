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
 * T003 (US1): el technician asignado a una orden {@code assigned} la marca
 * como iniciada ({@code in_progress}) -> 200 (FR-001, SC-001). Verifica el
 * nuevo estado tanto en la respuesta como releyendo de la base de datos.
 */
class OrderStartByTechnicianTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ASSIGNED_ORDER_ID =
      UUID.fromString("a2222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void assignedTechnicianStartsWorkAndOrderMovesToInProgress() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", ASSIGNED_ORDER_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "in_progress"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("in_progress"));

    Order reloaded = orderRepository.findById(ASSIGNED_ORDER_ID).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.in_progress);
  }
}
