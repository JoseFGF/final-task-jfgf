package com.prueba.dto;

import com.prueba.model.EvidencePhoto;
import com.prueba.model.Order;
import com.prueba.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schema {@code OrderDetail} de `contracts/openapi.yaml` (US1, FR-001 a
 * FR-004): incluye la nota y evidencia de ejecución, y el comentario del
 * último rechazo (FR-022) si lo hubo.
 */
public record OrderDetailResponse(
    UUID id,
    OrderStatus status,
    UUID assignedTechnicianId,
    Instant createdAt,
    Instant updatedAt,
    String executionNote,
    List<UUID> evidencePhotoIds,
    String rejectionComment) {

  public static OrderDetailResponse from(Order order) {
    return new OrderDetailResponse(
        order.getId(),
        order.getStatus(),
        technicianIdOf(order),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getExecutionNote(),
        order.getEvidencePhotos().stream().map(EvidencePhoto::getId).toList(),
        order.getRejectionComment());
  }

  private static UUID technicianIdOf(Order order) {
    return order.getAssignedTechnician() != null ? order.getAssignedTechnician().getId() : null;
  }
}
