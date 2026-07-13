package com.prueba.service;

import com.prueba.dto.OrderDetailResponse;
import com.prueba.dto.ReviewRequest;
import com.prueba.exception.ConflictException;
import com.prueba.exception.NotFoundException;
import com.prueba.exception.ValidationException;
import com.prueba.model.Order;
import com.prueba.model.OrderStatus;
import com.prueba.repository.OrderRepository;
import com.prueba.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprobación/rechazo de una orden en revisión (US3: FR-009 a FR-012a). Solo
 * aplica sobre órdenes en {@code pending_review}; aprobar cierra la orden,
 * rechazar exige un comentario no vacío y la devuelve a {@code in_progress}
 * para que el technician la corrija. El control de rol (SUPERVISOR) vive en
 * el controller vía {@code @PreAuthorize}.
 */
@Service
public class ReviewService {

  private final OrderRepository orderRepository;

  public ReviewService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Transactional
  public OrderDetailResponse review(CurrentUser currentUser, UUID orderId, ReviewRequest request) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));

    ensurePendingReview(order);

    if (request.decision() == ReviewRequest.ReviewDecision.APPROVE) {
      approve(order);
    } else {
      reject(order, request.comment());
    }

    return OrderDetailResponse.from(order);
  }

  /** FR-010: solo se puede aprobar/rechazar una orden en {@code pending_review}. */
  private void ensurePendingReview(Order order) {
    if (order.getStatus() != OrderStatus.pending_review) {
      throw new ConflictException(
          "La orden debe estar en estado pending_review para revisarla, está en "
              + order.getStatus());
    }
  }

  /** FR-011: aprobar cierra la orden. */
  private void approve(Order order) {
    order.setStatus(OrderStatus.closed);
  }

  /** FR-012, FR-012a: rechazar exige comentario no vacío y devuelve la orden a in_progress. */
  private void reject(Order order, String comment) {
    if (comment == null || comment.isBlank()) {
      throw new ValidationException("Se requiere un comentario para rechazar la orden");
    }
    order.setRejectionComment(comment);
    order.setStatus(OrderStatus.in_progress);
  }
}
