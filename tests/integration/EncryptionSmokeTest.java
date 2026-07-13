import static org.assertj.core.api.Assertions.assertThat;

import com.fieldops.FieldopsApplication;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.yaml.snakeyaml.Yaml;

/**
 * T056a (FR-019, FR-020, SC-006): smoke test del cifrado en tránsito.
 *
 * <p>El perfil por defecto/de test (ver {@link BaseIntegrationTest}) sirve
 * HTTP plano deliberadamente: forzar HTTPS ahí obligaría a toda la suite a
 * gestionar TLS sin aportar cobertura adicional sobre FR-019/FR-020, que son
 * responsabilidad exclusiva del perfil de despliegue {@code prod}
 * (application-prod.yml, activado en docker-compose.yml). Este test activa
 * ese perfil explícitamente y verifica, contra un servidor real levantado en
 * un puerto aleatorio:
 *
 * <ul>
 *   <li>que una conexión HTTPS (con el keystore de desarrollo empaquetado)
 *       completa el handshake TLS correctamente;
 *   <li>que una conexión en texto plano contra ese mismo puerto es rechazada
 *       (Tomcat solo expone el connector HTTPS en este perfil, sin connector
 *       HTTP adicional);
 *   <li>que la configuración activa de ese perfil (leída directamente del
 *       YAML, sin necesidad de una conexión JDBC real por TLS) pide
 *       {@code sslmode=require} en la URL del datasource.
 * </ul>
 */
@SpringBootTest(
    classes = FieldopsApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Tag("integration")
class EncryptionSmokeTest {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("fieldops_test")
          .withUsername("fieldops")
          .withPassword("fieldops");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    // El contenedor de Testcontainers no ofrece TLS: se sobreescribe solo la
    // URL de conexión real usada por este test (para poder arrancar el
    // contexto), sin tocar el valor por defecto declarado en
    // application-prod.yml (que sí exige sslmode=require) y que se verifica
    // por separado leyendo el YAML directamente en jdbcUrlOfProdProfileRequiresSsl().
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort private int port;

  @Test
  void httpsConnectionCompletesTlsHandshake() throws Exception {
    SSLContext trustAllContext = trustAllSslContext();
    try (SSLSocket socket =
        (SSLSocket) trustAllContext.getSocketFactory().createSocket("localhost", port)) {
      socket.startHandshake();
      assertThat(socket.getSession().isValid()).isTrue();
    }
  }

  @Test
  void plainHttpConnectionIsRejected() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      socket.setSoTimeout(2000);
      socket
          .getOutputStream()
          .write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.UTF_8));
      socket.getOutputStream().flush();

      String response;
      try {
        response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException expected) {
        // Tomcat cierra/reinicia la conexión al recibir texto plano en un
        // connector TLS: una IOException al leer también es un rechazo
        // válido (no llega a servir la aplicación en claro).
        return;
      }

      // El connector TLS de Tomcat detecta una petición HTTP en claro y
      // responde con un 400 explicando que la conexión requiere TLS, en vez
      // de servir la aplicación sin cifrar: nunca debe llegar una respuesta
      // 2xx con contenido de la API.
      assertThat(response).doesNotContain("HTTP/1.1 200");
      assertThat(response).contains("400");
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void jdbcUrlOfProdProfileRequiresSsl() throws Exception {
    Map<String, Object> config = loadProdProfileYaml();
    Map<String, Object> spring = (Map<String, Object>) config.get("spring");
    Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
    String url = (String) datasource.get("url");

    assertThat(url).contains("sslmode=require");
  }

  @Test
  void dockerComposeActivatesProdProfileAndRequiresSslOnDatasource() throws Exception {
    Path dockerComposePath = repoRoot().resolve("docker-compose.yml");
    String dockerCompose = Files.readString(dockerComposePath);

    assertThat(dockerCompose).contains("SPRING_PROFILES_ACTIVE: prod");
    assertThat(dockerCompose).contains("sslmode=require");
  }

  private Map<String, Object> loadProdProfileYaml() throws IOException {
    try (InputStream in =
        new ClassPathResource("application-prod.yml").getInputStream()) {
      return new Yaml().load(in);
    }
  }

  private Path repoRoot() {
    // Los módulos de test comparten sources vía build-helper-maven-plugin
    // (pom.xml del backend); el directorio de trabajo de Maven es el
    // basedir del módulo backend (src/backend), dos niveles por debajo de
    // la raíz del repo (donde vive docker-compose.yml).
    return Path.of("").toAbsolutePath().resolve("../../").normalize();
  }

  private SSLContext trustAllSslContext() throws Exception {
    TrustManager trustAll =
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType) {}

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType) {}

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
        };
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(null, new TrustManager[] {trustAll}, new SecureRandom());
    return context;
  }
}
