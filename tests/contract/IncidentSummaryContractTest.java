import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * T047 (US5): contract test de {@code POST
 * /orders/{orderId}/incident-summary} contra contracts/openapi.yaml — 200,
 * 401, 403, 404, 409 (FR-015, FR-016). {@link AnthropicClient} se sustituye
 * por un mock (@MockBean) para no depender de la API real de Anthropic ni
 * gastar cuota en CI.
 */
class IncidentSummaryContractTest extends BaseIntegrationTest {

  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  // Distinto del technician de seed usado por OrderVisibilityTechnicianTest (US1): esta
  // suite comparte la base de datos de Testcontainers entre clases de test, y crear
  // órdenes bajo el mismo technician de seed rompería esa aserción de conjunto exacto.
  private static final UUID TECHNICIAN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @MockBean private AnthropicClient anthropicClient;

  @Test
  void requestingASummaryForAPendingReviewOrderWithExecutionReturnsOk() throws Exception {
    when(anthropicClient.summarize(anyString())).thenReturn(Optional.of("Resumen breve de la incidencia."));
    Order order = createOrderWithNote(OrderStatus.pending_review, "El cliente reporta una fuga en la tubería.");
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc.perform(incidentSummaryRequest(order.getId(), token)).andExpect(status().isOk());
  }

  @Test
  void rejectsRequestWithoutSession() throws Exception {
    Order order = createOrderWithNote(OrderStatus.pending_review, "Nota suficiente para resumir.");

    mockMvc
        .perform(post("/api/v1/orders/{orderId}/incident-summary", order.getId()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsRoleOtherThanSupervisor() throws Exception {
    Order order = createOrderWithNote(OrderStatus.pending_review, "Nota suficiente para resumir.");
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc.perform(incidentSummaryRequest(order.getId(), token)).andExpect(status().isForbidden());
  }

  @Test
  void returnsNotFoundForAnUnknownOrder() throws Exception {
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(incidentSummaryRequest(UUID.randomUUID(), token))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsRequestWhenOrderHasNoExecutionRegistered() throws Exception {
    Order order = createOrderWithNote(OrderStatus.in_progress, null);
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc.perform(incidentSummaryRequest(order.getId(), token)).andExpect(status().isConflict());
  }

  private MockHttpServletRequestBuilder incidentSummaryRequest(UUID orderId, String token) {
    return post("/api/v1/orders/{orderId}/incident-summary", orderId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private Order createOrderWithNote(OrderStatus status, String note) {
    Order order = new Order(UUID.randomUUID(), status, userRepository.getReferenceById(TECHNICIAN_ID));
    order.setExecutionNote(note);
    return orderRepository.save(order);
  }
}
