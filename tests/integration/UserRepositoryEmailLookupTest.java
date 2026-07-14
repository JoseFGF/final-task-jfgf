import static org.assertj.core.api.Assertions.assertThat;

import com.fieldops.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T002: {@code findByEmailIgnoreCase} debe encontrar al usuario de seed
 * independientemente de la capitalización usada, y no encontrar nada para un
 * email inexistente (ADR-005, research.md).
 */
class UserRepositoryEmailLookupTest extends BaseIntegrationTest {

  @Autowired private UserRepository userRepository;

  @Test
  void findsUserRegardlessOfEmailCasing() {
    assertThat(userRepository.findByEmailIgnoreCase("Technician2@Fieldops.test")).isPresent();
    assertThat(userRepository.findByEmailIgnoreCase("TECHNICIAN2@FIELDOPS.TEST")).isPresent();
    assertThat(
            userRepository
                .findByEmailIgnoreCase("Technician2@Fieldops.test")
                .orElseThrow()
                .getEmail())
        .isEqualTo("technician2@fieldops.test");
  }

  @Test
  void returnsEmptyForNonExistentEmail() {
    assertThat(userRepository.findByEmailIgnoreCase("nadie@fieldops.test")).isEmpty();
  }
}
