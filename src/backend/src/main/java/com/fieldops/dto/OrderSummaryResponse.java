package com.fieldops.dto;

import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;

/** Respuesta de {@code GET /orders}, según el schema {@code OrderSummary} de contracts/openapi.yaml. */
public record OrderSummaryResponse(
    UUID id,
    OrderStatus status,
    UUID assignedTechnicianId,
    Instant createdAt,
    Instant updatedAt) {

  public static OrderSummaryResponse from(Order order) {
    UUID assignedTechnicianId =
        order.getAssignedTechnician() == null ? null : order.getAssignedTechnician().getId();
    return new OrderSummaryResponse(
        order.getId(), order.getStatus(), assignedTechnicianId, order.getCreatedAt(), order.getUpdatedAt());
  }
}
