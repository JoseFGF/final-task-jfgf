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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

/**
 * T050 (US5): si la llamada al proveedor de IA falla o hace timeout, el
 * sistema DEBE responder igual que en el caso de evidencia insuficiente —
 * nunca un 500 (research.md, ADR-001; Principio V, constitution). Este es el
 * test que prueba, no solo documenta, el fail-safe de
 * {@code IncidentSummaryService}.
 */
class IncidentSummaryProviderFailureTest extends BaseIntegrationTest {

  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  // Distinto del technician de seed usado por OrderVisibilityTechnicianTest (US1): esta
  // suite comparte la base de datos de Testcontainers entre clases de test, y crear
  // órdenes bajo el mismo technician de seed rompería esa aserción de conjunto exacto.
  private static final UUID TECHNICIAN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @MockBean private AnthropicClient anthropicClient;

  @Test
  void providerTimeoutIsTranslatedToInsufficientEvidenceNeverA500() throws Exception {
    when(anthropicClient.summarize(anyString()))
        .thenThrow(new ResourceAccessException("simulated timeout calling Anthropic API"));

    Order order =
        orderRepository.save(
            new Order(
                UUID.randomUUID(),
                OrderStatus.pending_review,
                userRepository.getReferenceById(TECHNICIAN_ID)));
    order.setExecutionNote("El técnico reemplazó la válvula defectuosa y probó el sistema.");
    orderRepository.save(order);

    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/incident-summary", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sufficient").value(false))
        .andExpect(jsonPath("$.summary").doesNotExist());
  }

  @Test
  void unexpectedRuntimeErrorIsTranslatedToInsufficientEvidenceNeverA500() throws Exception {
    when(anthropicClient.summarize(anyString()))
        .thenThrow(new RuntimeException("simulated parsing failure of the provider response"));

    Order order =
        orderRepository.save(
            new Order(
                UUID.randomUUID(),
                OrderStatus.pending_review,
                userRepository.getReferenceById(TECHNICIAN_ID)));
    order.setExecutionNote("El técnico reemplazó la válvula defectuosa y probó el sistema.");
    orderRepository.save(order);

    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/incident-summary", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sufficient").value(false))
        .andExpect(jsonPath("$.summary").doesNotExist());
  }
}
