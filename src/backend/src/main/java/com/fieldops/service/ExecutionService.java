package com.fieldops.service;

import com.fieldops.dto.OrderDetailResponse;
import com.fieldops.exception.ConflictException;
import com.fieldops.exception.ForbiddenException;
import com.fieldops.exception.NotFoundException;
import com.fieldops.exception.ValidationException;
import com.fieldops.model.EvidencePhoto;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.security.CurrentUser;
import com.fieldops.storage.FileStorageService;
import com.fieldops.storage.FileStorageService.StoredFile;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Registro de la ejecución de una orden (US2, FR-005 a FR-008): solo el
 * TECHNICIAN asignado, solo sobre una orden {@code in_progress}, con al
 * menos una foto de evidencia válida. Al completarse, la orden pasa a
 * {@code pending_review} (FR-008).
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
    // Defensa en profundidad (research.md, STRIDE): no confiar solo en el
    // @PreAuthorize del controller para una operación que cambia datos.
    if (currentUser.role() != Role.TECHNICIAN) {
      throw new ForbiddenException("Solo un technician puede registrar la ejecución de una orden");
    }

    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));

    requireAssignedTo(order, currentUser.id());
    requireInProgress(order);
    requireNonBlankNote(note);
    requireAtLeastOnePhoto(photos);

    // Valida todas las fotos antes de guardar cualquiera en disco (FR-006a):
    // si una es inválida, la ejecución no debe quedar registrada ni a medias.
    photos.forEach(fileStorageService::validateImage);

    for (MultipartFile photo : photos) {
      StoredFile stored = fileStorageService.store(photo, orderId);
      order
          .getEvidencePhotos()
          .add(
              new EvidencePhoto(
                  UUID.randomUUID(), order, stored.path(), stored.contentType(), stored.sizeBytes()));
    }

    order.setExecutionNote(note);
    order.setStatus(OrderStatus.pending_review);

    return OrderDetailResponse.from(order);
  }

  private void requireAssignedTo(Order order, UUID technicianId) {
    if (order.getAssignedTechnician() == null
        || !order.getAssignedTechnician().getId().equals(technicianId)) {
      throw new ForbiddenException("La orden no está asignada a este technician");
    }
  }

  private void requireInProgress(Order order) {
    if (order.getStatus() != OrderStatus.in_progress) {
      throw new ConflictException("La orden debe estar en in_progress para registrar su ejecución");
    }
  }

  private void requireNonBlankNote(String note) {
    if (note == null || note.isBlank()) {
      throw new ValidationException("La nota de ejecución no puede estar en blanco");
    }
  }

  private void requireAtLeastOnePhoto(List<MultipartFile> photos) {
    if (photos == null || photos.isEmpty()) {
      throw new ValidationException("Se requiere al menos una foto de evidencia");
    }
  }
}
