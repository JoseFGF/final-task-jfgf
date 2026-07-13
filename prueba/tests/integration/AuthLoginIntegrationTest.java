import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-024: el token emitido por {@code POST /auth/login} realmente sirve para
 * llamar a un endpoint protegido de la API, no solo tiene forma de JWT
 * válido. Como esta base común no implementa aún ningún controller de
 * negocio ({@code /orders} lo añade otro agente en paralelo), se usa el
 * mismo {@link SupervisorOnlyTestController} de {@link AccessAuditTest},
 * en su endpoint que solo exige sesión válida (sin restricción de rol).
 */
@Import(SupervisorOnlyTestController.class)
class AuthLoginIntegrationTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void tokenFromLoginIsAcceptedByAProtectedEndpoint() throws Exception {
    String responseBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "email", "dispatcher@prueba.test",
                                "password", "password123"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode json = objectMapper.readTree(responseBody);
    String token = json.get("token").asText();

    mockMvc
        .perform(
            get("/test-support/authenticated-only")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
  }
}
