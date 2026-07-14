import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * T036 (US4): contract test de {@code GET
 * /orders/{orderId}/evidence-photos/{photoId}} — 200/401/403/404 (FR-009,
 * FR-009a). Registra primero una ejecución real con foto (mismo mecanismo
 * que {@code ExecutionRegisterTest}) para obtener un {@code storagePath}
 * real en el volumen de test, en vez de depender de las rutas de seed
 * (que no apuntan a archivos existentes en disco).
 */
class EvidencePhotoContentContractTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void returnsThePhotoContentForAnAuthorizedUser() throws Exception {
    OrderWithPhoto orderWithPhoto = registerOrderWithPhoto();
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            get(
                    "/api/v1/orders/{orderId}/evidence-photos/{photoId}",
                    orderWithPhoto.orderId(),
                    orderWithPhoto.photoId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsRequestWithoutSession() throws Exception {
    OrderWithPhoto orderWithPhoto = registerOrderWithPhoto();

    mockMvc
        .perform(
            get(
                "/api/v1/orders/{orderId}/evidence-photos/{photoId}",
                orderWithPhoto.orderId(),
                orderWithPhoto.photoId()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsATechnicianNotAssignedToTheOrder() throws Exception {
    OrderWithPhoto orderWithPhoto = registerOrderWithPhoto();
    String token = jwtService.generateToken(OTHER_TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            get(
                    "/api/v1/orders/{orderId}/evidence-photos/{photoId}",
                    orderWithPhoto.orderId(),
                    orderWithPhoto.photoId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsNotFoundWhenThePhotoDoesNotBelongToTheOrder() throws Exception {
    OrderWithPhoto orderWithPhoto = registerOrderWithPhoto();
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            get(
                    "/api/v1/orders/{orderId}/evidence-photos/{photoId}",
                    orderWithPhoto.orderId(),
                    UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  private OrderWithPhoto registerOrderWithPhoto() throws Exception {
    Order order =
        orderRepository.save(
            new Order(
                UUID.randomUUID(), OrderStatus.in_progress, userRepository.getReferenceById(TECHNICIAN_ID)));
    String technicianToken = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    String response =
        mockMvc
            .perform(
                multipart("/api/v1/orders/{orderId}/execution", order.getId())
                    .file(
                        new MockMultipartFile(
                            "photos", "evidence.png", "image/png", TestImages.validPng()))
                    .param("note", "Se revisó el equipo y quedó operativo.")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicianToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID photoId =
        UUID.fromString(objectMapper.readTree(response).get("evidencePhotoIds").get(0).asText());
    return new OrderWithPhoto(order.getId(), photoId);
  }

  private record OrderWithPhoto(UUID orderId, UUID photoId) {}
}
