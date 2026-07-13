package com.prueba.service;

import com.prueba.dto.OrderDetailResponse;
import com.prueba.dto.ReassignmentRequest;
import com.prueba.exception.ConflictException;
import com.prueba.exception.NotFoundException;
import com.prueba.model.Order;
import com.prueba.model.OrderStatus;
import com.prueba.model.User;
import com.prueba.repository.OrderRepository;
import com.prueba.repository.UserRepository;
import com.prueba.security.CurrentUser;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Reasignación de una orden a otro technician (US4: FR-013, FR-014, FR-021).
 * Solo aplica sobre órdenes en {@code assigned}, {@code in_progress} o
 * {@code pending_review} (no en {@code closed}); si la orden está en
 * {@code pending_review}, reasignar cambia únicamente el technician, sin
 * alterar el estado ni la revisión en curso. El control de rol (DISPATCHER)
 * vive en el controller vía {@code @PreAuthorize}.
 *
 * <p>Ante reasignaciones concurrentes sobre la misma orden (FR-021), cada
 * intento lee y escribe la orden en transacciones independientes (delegadas
 * en los métodos, ya transaccionales, de {@link OrderRepository}); si el
 * bloqueo optimista de {@code version} detecta un choque al escribir, se
 * reintenta releyendo el estado más reciente y reaplicando la reasignación,
 * de forma que el cliente siempre reciba 200 en vez de un error de
 * concurrencia.
 */
@Service
public class ReassignmentService {

  private static final int MAX_ATTEMPTS = 5;

  private static final Set<OrderStatus> REASSIGNABLE_STATUSES =
      EnumSet.of(OrderStatus.assigned, OrderStatus.in_progress, OrderStatus.pending_review);

  private final OrderRepository orderRepository;
  private final UserRepository userRepository;

  public ReassignmentService(OrderRepository orderRepository, UserRepository userRepository) {
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
  }

  public OrderDetailResponse reassign(
      CurrentUser currentUser, UUID orderId, ReassignmentRequest request) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return attemptReassign(currentUser, orderId, request);
      } catch (ObjectOptimisticLockingFailureException concurrentUpdate) {
        if (attempt == MAX_ATTEMPTS) {
          throw concurrentUpdate;
        }
        // Otra reasignación concurrente ganó esta vuelta; se relee la orden
        // (con su version más reciente) y se reintenta (FR-021).
      }
    }
    throw new IllegalStateException("Número de reintentos de reasignación agotado");
  }

  private OrderDetailResponse attemptReassign(
      CurrentUser currentUser, UUID orderId, ReassignmentRequest request) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));

    ensureReassignable(order);

    User newTechnician =
        userRepository
            .findById(request.newTechnicianId())
            .orElseThrow(
                () -> new NotFoundException("Technician no encontrado: " + request.newTechnicianId()));

    order.setAssignedTechnician(newTechnician);
    order.setLastReassignedBy(userRepository.getReferenceById(currentUser.id()));
    order.setLastReassignedAt(Instant.now());

    Order saved = orderRepository.saveAndFlush(order);
    return OrderDetailResponse.from(saved);
  }

  /** FR-013, FR-014: solo se puede reasignar si la orden no está cerrada. */
  private void ensureReassignable(Order order) {
    if (!REASSIGNABLE_STATUSES.contains(order.getStatus())) {
      throw new ConflictException(
          "La orden debe estar en estado assigned, in_progress o pending_review para"
              + " reasignarla, está en "
              + order.getStatus());
    }
  }
}
