import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test de {@code POST /auth/login} contra
 * prueba/contracts/openapi.yaml (FR-024): 200 con un token cuando las
 * credenciales son correctas, 401 cuando no lo son — sin distinguir en la
 * respuesta si falló el email o la password, para no filtrar qué emails
 * existen.
 */
class AuthLoginContractTest extends BaseIntegrationTest {

  private static final String LOGIN_PATH = "/api/v1/auth/login";
  private static final String SEED_PASSWORD = "password123";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void validCredentialsReturnAToken() throws Exception {
    mockMvc
        .perform(loginRequest("dispatcher@prueba.test", SEED_PASSWORD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @Test
  void wrongPasswordIsRejected() throws Exception {
    mockMvc
        .perform(loginRequest("dispatcher@prueba.test", "not-the-right-password"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void unknownEmailIsRejected() throws Exception {
    mockMvc
        .perform(loginRequest("nobody@prueba.test", SEED_PASSWORD))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
      String email, String password) throws Exception {
    return post(LOGIN_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password)));
  }
}
