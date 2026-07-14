package com.fieldops.storage;

import com.fieldops.exception.NotFoundException;
import com.fieldops.exception.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda la evidencia fotográfica en el volumen de disco configurado por
 * {@code EVIDENCE_STORAGE_PATH} (ADR-004, research.md); la base de datos solo
 * guarda la ruta y metadata (data-model.md).
 *
 * <p>{@link #validateImage(MultipartFile)} cubre FR-006a: no basta con
 * confiar en el {@code Content-Type} declarado por el cliente (fácil de
 * falsificar), así que además se intenta decodificar el archivo como imagen
 * real antes de aceptarlo.
 */
@Component
public class FileStorageService {

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

  private final Path basePath;

  public FileStorageService(@Value("${evidence.storage-path}") String storagePath) {
    this.basePath = Path.of(storagePath);
  }

  /** Rechaza (422, FR-006a) un archivo vacío, con un tipo no soportado, o que no decodifica como imagen real. */
  public void validateImage(MultipartFile photo) {
    if (photo == null || photo.isEmpty()) {
      throw new ValidationException("La foto de evidencia está vacía");
    }
    String contentType = photo.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw new ValidationException(
          "Formato de imagen no soportado (se espera image/jpeg o image/png): " + contentType);
    }
    if (!decodesAsRealImage(photo)) {
      throw new ValidationException("El archivo de evidencia no es una imagen válida o está corrupto");
    }
  }

  /** Guarda el archivo ya validado en {@code {basePath}/{orderId}/}, devolviendo su metadata persistible. */
  public StoredFile store(MultipartFile photo, UUID orderId) {
    try {
      Path orderDirectory = basePath.resolve(orderId.toString());
      Files.createDirectories(orderDirectory);
      Path target = orderDirectory.resolve(UUID.randomUUID() + extensionFor(photo.getContentType()));
      photo.transferTo(target);
      return new StoredFile(target.toString(), photo.getContentType(), Files.size(target));
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo guardar la foto de evidencia", e);
    }
  }

  /**
   * Carga como {@link Resource} el archivo ya guardado en {@code
   * storagePath} (ruta absoluta persistida por {@link #store}), para
   * servirlo en streaming (US4, ADR-006). Lanza {@link NotFoundException}
   * (404) si el archivo referenciado ya no existe en el volumen — el
   * llamador decide cómo reaccionar (FR-010: el resto de fotos de la orden
   * debe seguir mostrándose).
   */
  public Resource loadAsResource(String storagePath) {
    try {
      Path file = Path.of(storagePath);
      Resource resource = new UrlResource(file.toUri());
      if (!resource.exists() || !resource.isReadable()) {
        throw new NotFoundException("La foto de evidencia ya no existe en el almacenamiento");
      }
      return resource;
    } catch (MalformedURLException e) {
      throw new NotFoundException("La foto de evidencia ya no existe en el almacenamiento");
    }
  }

  private boolean decodesAsRealImage(MultipartFile photo) {
    try (ImageInputStream imageInputStream =
        ImageIO.createImageInputStream(new ByteArrayInputStream(photo.getBytes()))) {
      if (imageInputStream == null) {
        return false;
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
      if (!readers.hasNext()) {
        return false;
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(imageInputStream);
        // Fuerza la decodificación completa del contenido, no solo la lectura de cabecera.
        reader.read(0);
        return true;
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  private String extensionFor(String contentType) {
    return "image/png".equalsIgnoreCase(contentType) ? ".png" : ".jpg";
  }

  /** Metadata de un archivo ya persistido en disco, lista para guardar en {@code EvidencePhoto}. */
  public record StoredFile(String path, String contentType, long sizeBytes) {}
}
