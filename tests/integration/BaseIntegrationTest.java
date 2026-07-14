import com.fieldops.FieldopsApplication;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para tests de integración/contrato (T011): levanta un PostgreSQL 16
 * real vía Testcontainers (ADR-003, research.md) en vez de un mock de base de
 * datos, y arranca el contexto Spring completo apuntando a él. Las
 * migraciones Flyway (V1/V2) se aplican igual que en un entorno real.
 *
 * <p>El contenedor se arranca una única vez por JVM en un bloque estático
 * ("singleton container", patrón recomendado por Testcontainers) en vez de
 * dejar que {@code @Testcontainers}/{@code @Container} lo pare y lo reinicie
 * entre clases de test: como Spring cachea el {@code ApplicationContext} por
 * configuración (incluida esta clase base, compartida por todas las
 * historias de usuario), reiniciar el contenedor entre clases dejaría el
 * contexto cacheado apuntando a un puerto ya cerrado. Docker/Testcontainers
 * limpia el contenedor al terminar la JVM vía Ryuk, sin necesidad de
 * detenerlo explícitamente aquí.
 *
 * <p><b>Aislamiento entre tests</b>: al compartir un único contenedor para
 * toda la JVM, cualquier orden/foto/registro de auditoría que un test cree
 * queda visible para el resto si no se limpia — descubierto en CI (GitHub
 * Actions ejecuta las clases de test en otro orden que en local) cuando
 * {@code OrderVisibilityTechnicianTest} vio órdenes creadas por otras clases.
 * Se resetea la base de datos completa a los datos de seed antes de cada
 * método de test, reutilizando los mismos scripts SQL de Flyway (sin
 * duplicar los datos de seed en dos sitios).
 */
@SpringBootTest(
    classes = FieldopsApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Tag("integration")
public abstract class BaseIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("fieldops_test")
          .withUsername("fieldops")
          .withPassword("fieldops");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private DataSource dataSource;

  @BeforeEach
  void resetDatabaseToSeedState() throws Exception {
    // Una única conexión con autoCommit explícito para todo el reset: el
    // pool de Hikari de este proyecto se configura con autoCommit=false
    // (habitual junto con Hibernate/JPA), así que una conexión obtenida
    // fuera de una transacción gestionada por Spring puede dejar un DELETE
    // sin confirmar ("idle in transaction"), bloqueando en seco al INSERT
    // del reseed hecho desde otra conexión — descubierto con un thread dump
    // real tras un colgado reproducible en CI.
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(true);
      try (Statement statement = connection.createStatement()) {
        statement.execute("DELETE FROM evidence_photos");
        statement.execute("DELETE FROM access_audit_log");
        statement.execute("DELETE FROM orders");
        statement.execute("DELETE FROM users");
      }
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V2__seed_data.sql"));
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V3__seed_login_passwords.sql"));
    }
  }
}
