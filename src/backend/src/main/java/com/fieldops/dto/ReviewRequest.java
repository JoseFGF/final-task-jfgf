package com.fieldops.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body de {@code POST /orders/{orderId}/review} (US3), según
 * contracts/openapi.yaml: {@code comment} es obligatorio y no vacío solo
 * cuando {@code decision} es {@code REJECT} (FR-012a); se valida en
 * {@link com.fieldops.service.ReviewService}, no aquí, porque depende del
 * valor de otro campo.
 */
public record ReviewRequest(@NotNull ReviewDecision decision, String comment) {

  public enum ReviewDecision {
    APPROVE,
    REJECT
  }
}
