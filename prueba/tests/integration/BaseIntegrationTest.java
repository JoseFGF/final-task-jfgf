import com.prueba.PruebaBackendApplication;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para tests de integración/contrato: levanta un PostgreSQL 16 real vía
 * Testcontainers en vez de un mock de base de datos, y arranca el contexto
 * Spring completo apuntando a él. Las migraciones Flyway (V1/V2) se aplican
 * igual que en un entorno real. Esta réplica no usa docker-compose: el
 * backend corre en local sobre HTTP plano; Testcontainers gestiona su propio
 * contenedor Docker internamente para los tests.
 *
 * <p>El contenedor se arranca una única vez por JVM en un bloque estático
 * ("singleton container", patrón recomendado por Testcontainers) en vez de
 * dejar que {@code @Testcontainers}/{@code @Container} lo pare y lo reinicie
 * entre clases de test: como Spring cachea el {@code ApplicationContext} por
 * configuración (incluida esta clase base, compartida por todos los tests),
 * reiniciar el contenedor entre clases dejaría el contexto cacheado
 * apuntando a un puerto ya cerrado. Docker/Testcontainers limpia el
 * contenedor al terminar la JVM vía Ryuk, sin necesidad de detenerlo
 * explícitamente aquí.
 */
@SpringBootTest(
    classes = PruebaBackendApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Tag("integration")
public abstract class BaseIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("prueba_test")
          .withUsername("prueba")
          .withPassword("prueba");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
