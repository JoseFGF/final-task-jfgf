import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.JwtService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T065 (SC-005): smoke test de rendimiento local para las cuatro acciones
 * principales del sistema (consultar órdenes, registrar ejecución,
 * aprobar/rechazar, reasignar), que deben responder en menos de 2 segundos
 * "bajo condiciones normales de uso" (spec.md, SC-005).
 *
 * <p><b>Alcance y limitación explícita</b>: esto NO es una prueba de carga
 * (no hay concurrencia, ni JMeter/Gatling, ni volumen realista de datos). Es
 * una única petición contra la BD de Testcontainers en este entorno de test,
 * pensada para detectar que el propio backend no introduce lentitud
 * artificial (p. ej. N+1 queries, bloqueos innecesarios) bajo el caso más
 * simple. La latencia de red de un entorno de producción real no está
 * cubierta por este test y puede variar independientemente de este
 * resultado.
 */
class PerformanceSmokeTest extends BaseIntegrationTest {

  private static final Duration MAX_RESPONSE_TIME = Duration.ofSeconds(2);

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void listOrdersRespondsWithinTwoSeconds() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    measure(
        "GET /orders",
        () ->
            mockMvc
                .perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()));
  }

  @Test
  void registerExecutionRespondsWithinTwoSeconds() throws Exception {
    Order order = createOrder(OrderStatus.in_progress, TECHNICIAN_ID);
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    measure(
        "POST /orders/{id}/execution",
        () ->
            mockMvc
                .perform(
                    multipart("/api/v1/orders/{orderId}/execution", order.getId())
                        .file(
                            new MockMultipartFile(
                                "photos", "evidence.png", "image/png", TestImages.validPng()))
                        .param("note", "Reparación completada, equipo operativo de nuevo.")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()));
  }

  @Test
  void reviewRespondsWithinTwoSeconds() throws Exception {
    Order order = createOrder(OrderStatus.pending_review, TECHNICIAN_ID);
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    measure(
        "POST /orders/{id}/review",
        () ->
            mockMvc
                .perform(
                    post("/api/v1/orders/{orderId}/review", order.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "APPROVE"))))
                .andExpect(status().isOk()));
  }

  @Test
  void reassignmentRespondsWithinTwoSeconds() throws Exception {
    Order order = createOrder(OrderStatus.assigned, TECHNICIAN_ID);
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    measure(
        "POST /orders/{id}/reassignment",
        () ->
            mockMvc
                .perform(
                    post("/api/v1/orders/{orderId}/reassignment", order.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                Map.of("newTechnicianId", OTHER_TECHNICIAN_ID.toString()))))
                .andExpect(status().isOk()));
  }

  private Order createOrder(OrderStatus status, UUID technicianId) {
    return orderRepository.save(
        new Order(UUID.randomUUID(), status, userRepository.getReferenceById(technicianId)));
  }

  /** Ejecuta {@code call} y falla si tarda más de {@link #MAX_RESPONSE_TIME} (SC-005). */
  private void measure(String label, MockMvcCall call) throws Exception {
    long start = System.nanoTime();
    call.perform();
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(elapsed)
        .as("%s debería responder en menos de %s (SC-005), tardó %s", label, MAX_RESPONSE_TIME, elapsed)
        .isLessThan(MAX_RESPONSE_TIME);
  }

  @FunctionalInterface
  private interface MockMvcCall {
    void perform() throws Exception;
  }
}
