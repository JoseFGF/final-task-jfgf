import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldops.model.EvidencePhoto;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.JwtService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T014 (US2): dispatcher/supervisor cambian manualmente {@code in_progress ->
 * pending_review} sobre una orden que YA tiene evidencia registrada -> 200
 * (FR-005). La orden seed {@code a3333333} está {@code in_progress} pero sin
 * evidencia, así que este test crea su propia orden {@code in_progress} con
 * al menos una foto ya registrada.
 */
class OrderStatusManualToPendingReviewTest extends BaseIntegrationTest {

  private static final UUID DISPATCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SUPERVISOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID TECHNICIAN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper objectMapper;

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"DISPATCHER", "SUPERVISOR"})
  void movingToPendingReviewWithEvidenceAlreadyRegisteredReturnsOk(Role role) throws Exception {
    Order order = createInProgressOrderWithEvidence();
    UUID userId = role == Role.DISPATCHER ? DISPATCHER_ID : SUPERVISOR_ID;
    String token = jwtService.generateToken(userId, role);

    mockMvc
        .perform(
            post("/api/v1/orders/{orderId}/status", order.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "pending_review"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending_review"));
  }

  private Order createInProgressOrderWithEvidence() {
    Order order =
        new Order(
            UUID.randomUUID(),
            OrderStatus.in_progress,
            userRepository.getReferenceById(TECHNICIAN_ID));
    order
        .getEvidencePhotos()
        .add(
            new EvidencePhoto(
                UUID.randomUUID(), order, "/data/evidence/test/photo-1.jpg", "image/jpeg", 12345));
    return orderRepository.save(order);
  }
}
