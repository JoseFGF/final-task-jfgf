package com.fieldops.service;

import com.fieldops.exception.NotFoundException;
import com.fieldops.model.EvidencePhoto;
import com.fieldops.model.Order;
import com.fieldops.security.CurrentUser;
import com.fieldops.storage.FileStorageService;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contenido binario de una foto de evidencia (US4, FR-009, FR-009a, ADR-006):
 * reutiliza exactamente la misma autorización que {@code GET
 * /orders/{orderId}} ({@link OrderService#requireOrderVisibleTo}) antes de
 * servir el binario, para que un TECHNICIAN no pueda ver fotos de una orden
 * que no tiene asignada solo por adivinar/conocer el {@code photoId}.
 */
@Service
public class EvidencePhotoService {

  private final OrderService orderService;
  private final FileStorageService fileStorageService;

  public EvidencePhotoService(OrderService orderService, FileStorageService fileStorageService) {
    this.orderService = orderService;
    this.fileStorageService = fileStorageService;
  }

  @Transactional(readOnly = true)
  public PhotoContent getPhotoContent(CurrentUser currentUser, UUID orderId, UUID photoId) {
    Order order = orderService.requireOrderVisibleTo(currentUser, orderId);
    EvidencePhoto photo =
        order.getEvidencePhotos().stream()
            .filter(candidate -> candidate.getId().equals(photoId))
            .findFirst()
            .orElseThrow(
                () -> new NotFoundException("La foto no pertenece a esta orden: " + photoId));

    Resource resource = fileStorageService.loadAsResource(photo.getStoragePath());
    return new PhotoContent(resource, photo.getContentType());
  }

  public record PhotoContent(Resource resource, String contentType) {}
}
