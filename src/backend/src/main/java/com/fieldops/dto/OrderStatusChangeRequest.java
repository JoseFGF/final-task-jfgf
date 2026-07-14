package com.fieldops.dto;

import com.fieldops.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body de {@code POST /orders/{orderId}/status} (004-order-state-transitions,
 * ADR-009), según contracts/openapi.yaml. Un único DTO compartido por la
 * transición del technician (US1) y la corrección manual de
 * dispatcher/supervisor (US2): la regla exacta de qué transición es válida
 * vive en {@link com.fieldops.service.OrderStatusService}, no en el DTO.
 */
public record OrderStatusChangeRequest(@NotNull OrderStatus status) {}
