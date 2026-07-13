package com.fieldops.service;

import com.fieldops.dto.OrderDetailResponse;
import com.fieldops.exception.ConflictException;
import com.fieldops.exception.ForbiddenException;
import com.fieldops.exception.NotFoundException;
import com.fieldops.exception.ValidationException;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprobación/rechazo de una orden en revisión (US3, FR-009 a FR-012a): solo
 * el SUPERVISOR, y solo sobre una orden {@code pending_review}. Aprobar
 * cierra la orden; rechazar exige un comentario no vacío y la devuelve a
 * {@code in_progress}, guardando el comentario (FR-022).
 */
@Service
public class ReviewService {

  private final OrderRepository orderRepository;

  public ReviewService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Transactional
  public OrderDetailResponse approve(CurrentUser currentUser, UUID orderId) {
    Order order = findPendingReviewOrderOrThrow(currentUser, orderId);
    order.setRejectionComment(null);
    order.setStatus(OrderStatus.closed);
    return OrderDetailResponse.from(order);
  }

  @Transactional
  public OrderDetailResponse reject(CurrentUser currentUser, UUID orderId, String comment) {
    requireNonBlankComment(comment);
    Order order = findPendingReviewOrderOrThrow(currentUser, orderId);
    order.setRejectionComment(comment);
    order.setStatus(OrderStatus.in_progress);
    return OrderDetailResponse.from(order);
  }

  private Order findPendingReviewOrderOrThrow(CurrentUser currentUser, UUID orderId) {
    // Defensa en profundidad (research.md, STRIDE): no confiar solo en el
    // @PreAuthorize del controller para una operación que cambia datos.
    if (currentUser.role() != Role.SUPERVISOR) {
      throw new ForbiddenException("Solo un supervisor puede aprobar o rechazar una orden");
    }

    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));

    if (order.getStatus() != OrderStatus.pending_review) {
      throw new ConflictException("La orden debe estar en pending_review para ser revisada");
    }

    return order;
  }

  private void requireNonBlankComment(String comment) {
    if (comment == null || comment.isBlank()) {
      throw new ValidationException("Se requiere un comentario para rechazar la orden");
    }
  }
}
