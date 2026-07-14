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
 * T037 (US4): dispatcher/supervisor obtienen el binario de cualquier foto;
 * un technician asignado obtiene las de su propia orden; un technician no
 * asignado recibe 403 (FR-009a).
 */
class EvidencePhotoAccessTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OTHER_TECHNICIAN_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void dispatcherCanAccessAnyPhoto() throws Exception {
    UUID orderId = createOrderWithPhoto();
    UUID photoId = onlyPhotoIdOf(orderId);
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}/evidence-photos/{photoId}", orderId, photoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void supervisorCanAccessAnyPhoto() throws Exception {
    UUID orderId = createOrderWithPhoto();
    UUID photoId = onlyPhotoIdOf(orderId);
    String token = jwtService.generateToken(SUPERVISOR_ID, Role.SUPERVISOR);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}/evidence-photos/{photoId}", orderId, photoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void assignedTechnicianCanAccessThePhotoOfTheirOwnOrder() throws Exception {
    UUID orderId = createOrderWithPhoto();
    UUID photoId = onlyPhotoIdOf(orderId);
    String token = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}/evidence-photos/{photoId}", orderId, photoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void unassignedTechnicianCannotAccessThePhoto() throws Exception {
    UUID orderId = createOrderWithPhoto();
    UUID photoId = onlyPhotoIdOf(orderId);
    String token = jwtService.generateToken(OTHER_TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            get("/api/v1/orders/{orderId}/evidence-photos/{photoId}", orderId, photoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  private UUID createOrderWithPhoto() throws Exception {
    Order order =
        orderRepository.save(
            new Order(
                UUID.randomUUID(), OrderStatus.in_progress, userRepository.getReferenceById(TECHNICIAN_ID)));
    String technicianToken = jwtService.generateToken(TECHNICIAN_ID, Role.TECHNICIAN);

    mockMvc
        .perform(
            multipart("/api/v1/orders/{orderId}/execution", order.getId())
                .file(
                    new MockMultipartFile(
                        "photos", "evidence.png", "image/png", TestImages.validPng()))
                .param("note", "Se revisó el equipo y quedó operativo.")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicianToken))
        .andExpect(status().isOk());

    return order.getId();
  }

  private UUID onlyPhotoIdOf(UUID orderId) throws Exception {
    String token = jwtService.generateToken(DISPATCHER_ID, Role.DISPATCHER);
    String response =
        mockMvc
            .perform(
                get("/api/v1/orders/{orderId}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("evidencePhotoIds").get(0).asText());
  }
}
