package com.fieldops.service;

import com.fieldops.ai.AnthropicClient;
import com.fieldops.dto.IncidentSummaryResponse;
import com.fieldops.exception.ConflictException;
import com.fieldops.exception.ForbiddenException;
import com.fieldops.exception.NotFoundException;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.security.CurrentUser;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el resumen automático de incidencia (US5, FR-015, FR-016): solo el
 * SUPERVISOR, y solo para una orden {@code pending_review} con una ejecución
 * registrada. Delega la generación en {@link AnthropicClient}, pero el
 * fail-safe de "evidencia insuficiente" (Principio V, ADR-001 en
 * research.md) se decide y se garantiza aquí, no en el cliente HTTP.
 */
@Service
public class IncidentSummaryService {

  private static final Logger log = LoggerFactory.getLogger(IncidentSummaryService.class);

  private final OrderRepository orderRepository;
  private final AnthropicClient anthropicClient;

  public IncidentSummaryService(OrderRepository orderRepository, AnthropicClient anthropicClient) {
    this.orderRepository = orderRepository;
    this.anthropicClient = anthropicClient;
  }

  @Transactional(readOnly = true)
  public IncidentSummaryResponse summarize(CurrentUser currentUser, UUID orderId) {
    // Defensa en profundidad (research.md, STRIDE): no confiar solo en el
    // @PreAuthorize del controller para una operación que expone datos de
    // ejecución al supervisor.
    if (currentUser.role() != Role.SUPERVISOR) {
      throw new ForbiddenException("Solo un supervisor puede solicitar el resumen de incidencia");
    }

    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));

    requirePendingReviewWithExecution(order);

    String executionNote = order.getExecutionNote();
    if (executionNote.isBlank()) {
      // Nota vacía/en blanco: evidencia insuficiente sin gastar la llamada al
      // proveedor externo (FR-016).
      return IncidentSummaryResponse.insufficient();
    }

    return summarizeSafely(executionNote);
  }

  private void requirePendingReviewWithExecution(Order order) {
    if (order.getStatus() != OrderStatus.pending_review || order.getExecutionNote() == null) {
      throw new ConflictException(
          "La orden debe estar en pending_review con una ejecución registrada para resumir su"
              + " incidencia");
    }
  }

  /**
   * Fail-safe intencional (Principio V, ADR-001 en research.md): cualquier
   * excepción al invocar al proveedor de IA (timeout, error HTTP, error de
   * parseo de la respuesta) se trata exactamente igual que "evidencia
   * insuficiente" — nunca debe propagarse como un error 500 al supervisor ni
   * sustituirse por un resumen inventado de repuesto. Este catch-all amplio
   * es deliberado, no una omisión: la fiabilidad del asistente frente a un
   * fallo del proveedor externo es un requisito de negocio (FR-016), no un
   * detalle de infraestructura.
   */
  private IncidentSummaryResponse summarizeSafely(String executionNote) {
    try {
      return anthropicClient
          .summarize(executionNote)
          .map(IncidentSummaryResponse::sufficient)
          .orElseGet(IncidentSummaryResponse::insufficient);
    } catch (Exception ex) {
      log.warn(
          "Fallo al invocar al proveedor de IA para el resumen de incidencia; se aplica el"
              + " fail-safe de evidencia insuficiente (Principio V)",
          ex);
      return IncidentSummaryResponse.insufficient();
    }
  }
}
