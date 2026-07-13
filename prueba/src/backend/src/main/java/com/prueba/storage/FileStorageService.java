package com.prueba.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Persiste el contenido binario de la evidencia fotografica (US2, FR-005) en
 * un directorio local configurable, sin Docker/volumenes. La ruta guardada en
 * {@code EvidencePhoto.storagePath} es la que devuelve {@link #store}.
 */
@Service
public class FileStorageService {

  private final Path baseDir;

  public FileStorageService(
      @Value("${evidence.storage-path:evidence-storage}") String storagePath) {
    this.baseDir = Path.of(storagePath).toAbsolutePath().normalize();
  }

  public String store(UUID orderId, byte[] content, String originalFilename) {
    try {
      Path orderDir = baseDir.resolve(orderId.toString());
      Files.createDirectories(orderDir);
      Path target = orderDir.resolve(UUID.randomUUID() + extensionOf(originalFilename));
      Files.write(target, content);
      return target.toString();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo guardar la foto de evidencia", e);
    }
  }

  /**
   * Extrae una extension segura del nombre original, descartando cualquier
   * caracter que no sea alfanumerico (en particular separadores de ruta y
   * secuencias de escape de directorio), para que un nombre de archivo
   * adversario no pueda inyectar segmentos de ruta en el path final resuelto
   * con {@link Path#resolve}.
   */
  private String extensionOf(String originalFilename) {
    if (originalFilename == null) {
      return "";
    }
    int dotIndex = originalFilename.lastIndexOf('.');
    if (dotIndex < 0) {
      return "";
    }
    String rawExtension = originalFilename.substring(dotIndex + 1).replaceAll("[^a-zA-Z0-9]", "");
    if (rawExtension.isEmpty()) {
      return "";
    }
    return "." + rawExtension.substring(0, Math.min(rawExtension.length(), 10));
  }
}
