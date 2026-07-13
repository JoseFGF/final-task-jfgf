package com.prueba.service;

import com.prueba.dto.OrderDetailResponse;
import com.prueba.exception.ConflictException;
import com.prueba.exception.ForbiddenException;
import com.prueba.exception.NotFoundException;
import com.prueba.exception.ValidationException;
import com.prueba.model.EvidencePhoto;
import com.prueba.model.Order;
import com.prueba.model.OrderStatus;
import com.prueba.repository.OrderRepository;
import com.prueba.security.CurrentUser;
import com.prueba.storage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Registro de la ejecución de una orden (US2: FR-005 a FR-008, FR-006a).
 * Solo el TECHNICIAN asignado puede registrar la ejecución de una orden
 * propia en estado {@code in_progress}; exige al menos una foto de evidencia
 * válida y, al completarse, mueve la orden a {@code pending_review}.
 */
@Service
public class ExecutionService {

  private final OrderRepository orderRepository;
  private final FileStorageService fileStorageService;

  public ExecutionService(OrderRepository orderRepository, FileStorageService fileStorageService) {
    this.orderRepository = orderRepository;
    this.fileStorageService = fileStorageService;
  }

  @Transactional
  public OrderDetailResponse registerExecution(
      CurrentUser currentUser, UUID orderId, String note, List<MultipartFile> photos) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));

    ensureAssignedTechnician(currentUser, order);
    ensureInProgress(order);
    ensureHasPhotos(photos);

    List<EvidencePhoto> evidencePhotos = photos.stream().map(photo -> toEvidencePhoto(order, photo)).toList();

    order.setExecutionNote(note);
    order.getEvidencePhotos().addAll(evidencePhotos);
    order.setStatus(OrderStatus.pending_review);

    return OrderDetailResponse.from(order);
  }

  /** FR-007: solo el TECHNICIAN al que está asignada la orden puede registrar su ejecución. */
  private void ensureAssignedTechnician(CurrentUser currentUser, Order order) {
    boolean isAssignedTechnician =
        order.getAssignedTechnician() != null
            && order.getAssignedTechnician().getId().equals(currentUser.id());
    if (!isAssignedTechnician) {
      throw new ForbiddenException("La orden no está asignada a este technician");
    }
  }

  /** FR-007: la orden debe estar en {@code in_progress} para registrar su ejecución. */
  private void ensureInProgress(Order order) {
    if (order.getStatus() != OrderStatus.in_progress) {
      throw new ConflictException(
          "La orden debe estar en estado in_progress para registrar su ejecución, está en "
              + order.getStatus());
    }
  }

  /** FR-006: se exige al menos una foto de evidencia. */
  private void ensureHasPhotos(List<MultipartFile> photos) {
    if (photos == null || photos.isEmpty() || photos.stream().allMatch(MultipartFile::isEmpty)) {
      throw new ValidationException("Se requiere al menos una foto de evidencia");
    }
  }

  /**
   * FR-006a: valida que la foto sea una imagen real (no solo por el
   * content-type declarado, sino leyendo el contenido con {@link ImageIO})
   * antes de persistirla.
   */
  private EvidencePhoto toEvidencePhoto(Order order, MultipartFile photo) {
    byte[] content = readBytes(photo);
    if (!isValidImage(content)) {
      throw new ValidationException(
          "El archivo '" + photo.getOriginalFilename() + "' no es una imagen válida");
    }
    String storagePath = fileStorageService.store(order.getId(), content, photo.getOriginalFilename());
    return new EvidencePhoto(
        UUID.randomUUID(), order, storagePath, photo.getContentType(), content.length);
  }

  private byte[] readBytes(MultipartFile photo) {
    try {
      return photo.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo leer la foto de evidencia adjunta", e);
    }
  }

  private boolean isValidImage(byte[] content) {
    try {
      return ImageIO.read(new ByteArrayInputStream(content)) != null;
    } catch (IOException e) {
      return false;
    }
  }
}
