package com.fieldops.dto;

import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;

/** Respuesta de {@code GET /orders}, según el schema {@code OrderSummary} de contracts/openapi.yaml. */
public record OrderSummaryResponse(
    UUID id,
    OrderStatus status,
    String description,
    String assignedTechnicianEmail,
    Instant createdAt,
    Instant updatedAt) {

  public static OrderSummaryResponse from(Order order) {
    String assignedTechnicianEmail =
        order.getAssignedTechnician() == null ? null : order.getAssignedTechnician().getEmail();
    return new OrderSummaryResponse(
        order.getId(),
        order.getStatus(),
        order.getDescription(),
        assignedTechnicianEmail,
        order.getCreatedAt(),
        order.getUpdatedAt());
  }
}
