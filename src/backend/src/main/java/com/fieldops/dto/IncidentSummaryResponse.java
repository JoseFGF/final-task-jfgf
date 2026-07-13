package com.fieldops.dto;

/**
 * Respuesta de {@code POST /orders/{orderId}/incident-summary}, según el
 * schema {@code IncidentSummaryResult} de contracts/openapi.yaml (FR-015,
 * FR-016). {@code summary} es {@code null} siempre que {@code sufficient} sea
 * {@code false} — nunca hay un resumen inventado (Principio V).
 */
public record IncidentSummaryResponse(boolean sufficient, String summary) {

  public static IncidentSummaryResponse sufficient(String summary) {
    return new IncidentSummaryResponse(true, summary);
  }

  public static IncidentSummaryResponse insufficient() {
    return new IncidentSummaryResponse(false, null);
  }
}
