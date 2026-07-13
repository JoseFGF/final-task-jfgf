package com.prueba.dto;

/**
 * Cuerpo de {@code POST /orders/{orderId}/review} (US3: FR-009 a FR-012a).
 * {@code comment} es obligatorio y no vacío solo cuando {@code decision} es
 * {@link ReviewDecision#REJECT} (FR-012a); se ignora si es {@code APPROVE}.
 */
public record ReviewRequest(ReviewDecision decision, String comment) {

  public enum ReviewDecision {
    APPROVE,
    REJECT
  }
}
