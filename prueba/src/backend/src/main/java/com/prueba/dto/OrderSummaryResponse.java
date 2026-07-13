package com.prueba.dto;

import com.prueba.model.Order;
import com.prueba.model.OrderStatus;
import java.time.Instant;
import java.util.UUID;

/** Schema {@code OrderSummary} de `contracts/openapi.yaml` (US1, FR-001 a FR-003). */
public record OrderSummaryResponse(
    UUID id,
    OrderStatus status,
    UUID assignedTechnicianId,
    Instant createdAt,
    Instant updatedAt) {

  public static OrderSummaryResponse from(Order order) {
    return new OrderSummaryResponse(
        order.getId(),
        order.getStatus(),
        technicianIdOf(order),
        order.getCreatedAt(),
        order.getUpdatedAt());
  }

  private static UUID technicianIdOf(Order order) {
    return order.getAssignedTechnician() != null ? order.getAssignedTechnician().getId() : null;
  }
}
