package com.fieldops.dto;

import com.fieldops.model.EvidencePhoto;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code GET /orders/{orderId}} y {@code POST
 * /orders/{orderId}/execution}, según el schema {@code OrderDetail} de
 * contracts/openapi.yaml. Incluye {@code rejectionComment} (FR-022): debe
 * viajar siempre en el JSON, aunque sea {@code null} si la orden nunca fue
 * rechazada o ya fue aprobada.
 */
public record OrderDetailResponse(
    UUID id,
    OrderStatus status,
    String description,
    String assignedTechnicianEmail,
    Instant createdAt,
    Instant updatedAt,
    String executionNote,
    List<UUID> evidencePhotoIds,
    String rejectionComment) {

  public static OrderDetailResponse from(Order order) {
    String assignedTechnicianEmail =
        order.getAssignedTechnician() == null ? null : order.getAssignedTechnician().getEmail();
    List<UUID> evidencePhotoIds =
        order.getEvidencePhotos().stream().map(EvidencePhoto::getId).toList();
    return new OrderDetailResponse(
        order.getId(),
        order.getStatus(),
        order.getDescription(),
        assignedTechnicianEmail,
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getExecutionNote(),
        evidencePhotoIds,
        order.getRejectionComment());
  }
}
