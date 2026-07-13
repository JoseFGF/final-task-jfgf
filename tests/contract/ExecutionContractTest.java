import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T024 (US2): contract test de {@code POST /orders/{orderId}/execution}
 * contra contracts/openapi.yaml — 200, 401, 403, 404, 409, 422 (FR-005 a
 * FR-008).
 *
 * <p>Usa al technician2 de seed (44444444...) como propietario de las
 * órdenes creadas ad-hoc para no interferir con el technician1 de seed
 * (22222222...), cuyo conjunto exacto de órdenes verifican los tests de
 * visibilidad (T016).
 */
class ExecutionContractTest extends BaseIntegrationTest {

  private static final UUID TECHNICIAN2_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID TECHNICIAN1_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID DISPATCHER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void registeringExecutionWithAPhotoReturnsOk() throws Exception {
    Order order = createOrder(OrderStatus.in_progress, TECHNICIAN2_ID);
    String token = jwtService.generateToken(TECHNICIAN2_ID, Role.TECHNICIAN);

    mockMvc.perform(executionRequest(order.getId(), token, "trabajo completado", validPhoto()))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsRequestWithoutSession() throws Exception {
    Order order = createOrder(OrderStatus.in_progress, TECHNICIAN2_ID);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", order.getId())
                .file(validPhoto())
                .param("note", "trabajo completado"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsTechnicianNotAssignedToTheOrder() throws Exception {
    Order order = createOrder(OrderStatus.in_progress, TECHNICIAN2_ID);
    String token = jwtService.generateToken(TECHNICIAN1_ID, Role.TECHNICIAN);

    mockMvc
        .perform(executionRequest(order.getId(), token, "trabajo completado", validPhoto()))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsRoleOtherThanTechnician() throws Exception {
    Order order = createOrder(OrderStatus.in_progress, TECHNICIAN2_ID);
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(executionRequest(order.getId(), token, "trabajo completado", validPhoto()))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsNotFoundForAnUnknownOrder() throws Exception {
    String token = jwtService.generateToken(TECHNICIAN2_ID, Role.TECHNICIAN);

    mockMvc
        .perform(executionRequest(UUID.randomUUID(), token, "trabajo completado", validPhoto()))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsRegistrationWhenOrderIsNotInProgress() throws Exception {
    Order order = createOrder(OrderStatus.assigned, TECHNICIAN2_ID);
    String token = jwtService.generateToken(TECHNICIAN2_ID, Role.TECHNICIAN);

    mockMvc
        .perform(executionRequest(order.getId(), token, "trabajo completado", validPhoto()))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsRegistrationWithoutAnyPhoto() throws Exception {
    Order order = createOrder(OrderStatus.in_progress, TECHNICIAN2_ID);
    String token = jwtService.generateToken(TECHNICIAN2_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .param("note", "trabajo completado"))
        .andExpect(status().isUnprocessableEntity());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      executionRequest(UUID orderId, String token, String note, MockMultipartFile photo) {
    return multipart("/api/v1/orders/{orderId}/execution", orderId)
        .file(photo)
        .param("note", note)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private MockMultipartFile validPhoto() {
    return new MockMultipartFile("photos", "evidence.png", "image/png", TestImages.validPng());
  }

  private Order createOrder(OrderStatus status, UUID technicianId) {
    Order order = new Order(UUID.randomUUID(), status, userRepository.getReferenceById(technicianId));
    return orderRepository.save(order);
  }
}
