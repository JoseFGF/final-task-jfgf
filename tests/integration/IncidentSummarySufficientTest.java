import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fieldops.ai.AnthropicClient;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;

/**
 * T048 (US5): con una nota de ejecución sustancial, el resumen es fiel al
 * contenido devuelto por el proveedor de IA (mock), {@code sufficient: true}
 * (FR-015).
 */
class IncidentSummarySufficientTest extends BaseIntegrationTest {

  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  // Distinto del technician de seed usado por OrderVisibilityTechnicianTest (US1): esta
  // suite comparte la base de datos de Testcontainers entre clases de test, y crear
  // órdenes bajo el mismo technician de seed rompería esa aserción de conjunto exacto.
  private static final UUID TECHNICIAN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @MockBean private AnthropicClient anthropicClient;

  @Test
  void substantialNoteReturnsSummaryFromTheProvider() throws Exception {
    String summaryFromProvider = "El técnico reparó una fuga en la tubería principal del baño.";
    when(anthropicClient.summarize(anyString())).thenReturn(Optional.of(summaryFromProvider));

    Order order =
        orderRepository.save(
            new Order(
                UUID.randomUUID(),
                OrderStatus.pending_review,
                userRepository.getReferenceById(TECHNICIAN_ID)));
    order.setExecutionNote(
        "Llegué a la vivienda del cliente y encontré una fuga importante en la tubería "
            + "principal del baño. Cerré la llave de paso, sustituí el tramo dañado y "
            + "comprobé que no quedaban fugas antes de irme.");
    orderRepository.save(order);

    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/incident-summary", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sufficient").value(true))
        .andExpect(jsonPath("$.summary").value(summaryFromProvider));
  }
}
