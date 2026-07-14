import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T038 (US4): una petición sin {@code Authorization} devuelve 401 aunque el
 * {@code photoId} (y el {@code orderId}) sean válidos — no hay acceso
 * público a las fotos de evidencia (FR-009a).
 */
class EvidencePhotoUnauthenticatedTest extends BaseIntegrationTest {

  private static final UUID ORDER_WITH_PHOTO = UUID.fromString("a4444444-4444-4444-4444-444444444444");
  private static final UUID PHOTO_ID = UUID.fromString("b1111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;

  @Test
  void rejectsRequestWithoutSessionEvenWithAValidPhotoId() throws Exception {
    mockMvc
        .perform(get("/api/v1/orders/{orderId}/evidence-photos/{photoId}", ORDER_WITH_PHOTO, PHOTO_ID))
        .andExpect(status().isUnauthorized());
  }
}
