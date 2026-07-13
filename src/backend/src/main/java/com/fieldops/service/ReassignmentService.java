package com.fieldops.service;

import com.fieldops.dto.OrderDetailResponse;
import com.fieldops.exception.ConflictException;
import com.fieldops.exception.ForbiddenException;
import com.fieldops.exception.NotFoundException;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.model.User;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.CurrentUser;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Reasignación de una orden a otro technician (US4, FR-013, FR-014, FR-021):
 * solo el DISPATCHER, y solo sobre órdenes en {@code assigned}, {@code
 * in_progress} o {@code pending_review}. Reasignar una orden en {@code
 * pending_review} cambia únicamente el technician responsable, sin alterar
 * el estado ni el resultado de la revisión en curso del supervisor.
 *
 * <p>Ante reasignaciones concurrentes sobre la misma orden (FR-021), el
 * bloqueo optimista de {@link Order#getVersion()} hace que una de las dos
 * escrituras falle con {@link OptimisticLockingFailureException}; en vez de
 * propagar un 500 al cliente, se reintenta releyendo la orden y reaplicando
 * la reasignación, de modo que la última reasignación válida procesada gane
 * sin dejar la orden en un estado contradictorio.
 */
@Service
public class ReassignmentService {

  private static final Set<OrderStatus> REASSIGNABLE_STATUSES =
      Set.of(OrderStatus.assigned, OrderStatus.in_progress, OrderStatus.pending_review);

  private static final int MAX_ATTEMPTS = 5;

  private final OrderRepository orderRepository;
  private final UserRepository userRepository;

  public ReassignmentService(OrderRepository orderRepository, UserRepository userRepository) {
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
  }

  public OrderDetailResponse reassign(CurrentUser currentUser, UUID orderId, UUID newTechnicianId) {
    requireDispatcher(currentUser);

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return doReassign(orderId, newTechnicianId);
      } catch (OptimisticLockingFailureException retryable) {
        if (attempt == MAX_ATTEMPTS) {
          throw retryable;
        }
        // Otra reasignación concurrente ganó esta ronda; se relee la orden
        // (versión más reciente) y se reintenta (FR-021).
      }
    }
    // Inalcanzable: el bucle siempre retorna o lanza en el último intento.
    throw new IllegalStateException("No se pudo completar la reasignación");
  }

  // Sin @Transactional aquí a propósito: cada intento debe leer la orden con
  // la versión más reciente confirmada y escribirla en su propia
  // transacción (findById/saveAndFlush ya son transaccionales por sí
  // mismos vía Spring Data). Envolver todo el método en una única
  // transacción impediría que un reintento vea la versión escrita por la
  // reasignación concurrente que ganó la ronda anterior.
  private OrderDetailResponse doReassign(UUID orderId, UUID newTechnicianId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));

    if (order.getStatus() == OrderStatus.closed
        || !REASSIGNABLE_STATUSES.contains(order.getStatus())) {
      throw new ConflictException(
          "La orden debe estar en assigned, in_progress o pending_review para reasignarse");
    }

    User newTechnician =
        userRepository
            .findById(newTechnicianId)
            .orElseThrow(
                () -> new NotFoundException("Technician no encontrado: " + newTechnicianId));

    order.setAssignedTechnician(newTechnician);
    Order saved = orderRepository.saveAndFlush(order);
    return OrderDetailResponse.from(saved);
  }

  private void requireDispatcher(CurrentUser currentUser) {
    // Defensa en profundidad (research.md, STRIDE): no confiar solo en el
    // @PreAuthorize del controller para una operación que cambia datos.
    if (currentUser.role() != Role.DISPATCHER) {
      throw new ForbiddenException("Solo un dispatcher puede reasignar una orden");
    }
  }
}
