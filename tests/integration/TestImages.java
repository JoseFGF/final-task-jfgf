import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Genera bytes de imagen válidos/corruptos en memoria para los tests de US2
 * (ejecución con evidencia), evitando depender de fixtures binarios en el
 * repositorio. Compartida entre {@code tests/contract} y
 * {@code tests/integration} (mismo paquete por defecto, mismo classpath de
 * test — ver `src/backend/pom.xml`, build-helper-maven-plugin).
 */
final class TestImages {

  private TestImages() {}

  static byte[] validPng() {
    try {
      BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static byte[] corruptImageBytes() {
    return "esto no es una imagen real".getBytes();
  }
}
