package com.fieldops.service;

import com.fieldops.dto.CreateOrderRequest;
import com.fieldops.dto.OrderDetailResponse;
import com.fieldops.dto.OrderSummaryResponse;
import com.fieldops.exception.ForbiddenException;
import com.fieldops.exception.NotFoundException;
import com.fieldops.exception.ValidationException;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.model.User;
import com.fieldops.repository.OrderRepository;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de órdenes (US1): visibilidad por rol (FR-001 a FR-003) y rechazo
 * de acceso al detalle de una orden ajena (FR-004). TECHNICIAN ve solo sus
 * órdenes asignadas; DISPATCHER y SUPERVISOR ven todas las órdenes del
 * sistema, sin distinción de estado.
 *
 * <p>Creación de órdenes nuevas (US3, FR-007 a FR-008): solo un DISPATCHER
 * puede crear una orden; si se indica un email de technician válido, la
 * orden nace directamente en {@code assigned} con auditoría de asignación
 * (FR-007c) — reutiliza el mismo criterio de resolución de technician que
 * {@link ReassignmentService} (ADR-005, research.md).
 */
@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final UserRepository userRepository;

  public OrderService(OrderRepository orderRepository, UserRepository userRepository) {
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public List<OrderSummaryResponse> listVisibleOrders(CurrentUser currentUser) {
    List<Order> orders =
        currentUser.role() == Role.TECHNICIAN
            ? orderRepository.findByAssignedTechnicianId(currentUser.id())
            : orderRepository.findAll();
    return orders.stream().map(OrderSummaryResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public OrderDetailResponse getOrderDetail(CurrentUser currentUser, UUID orderId) {
    Order order = requireOrderVisibleTo(currentUser, orderId);
    return OrderDetailResponse.from(order);
  }

  @Transactional
  public OrderDetailResponse createOrder(CurrentUser currentUser, CreateOrderRequest request) {
    requireDispatcher(currentUser);

    String description = request.description() == null ? "" : request.description().strip();
    if (description.isBlank()) {
      throw new ValidationException("La descripción de la orden es obligatoria");
    }

    Order order = new Order(UUID.randomUUID(), OrderStatus.draft, null);
    order.setDescription(description);

    String technicianEmail = request.technicianEmail();
    if (technicianEmail != null && !technicianEmail.isBlank()) {
      User technician = ReassignmentService.resolveTechnicianByEmail(userRepository, technicianEmail);
      User dispatcher =
          userRepository
              .findById(currentUser.id())
              .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + currentUser.id()));
      order.setAssignedTechnician(technician);
      order.setStatus(OrderStatus.assigned);
      order.setLastReassignedBy(dispatcher);
      order.setLastReassignedAt(Instant.now());
    }

    Order saved = orderRepository.saveAndFlush(order);
    return OrderDetailResponse.from(saved);
  }

  /**
   * Resuelve una orden y valida que {@code currentUser} tenga motivo, según
   * su rol, para verla (FR-004): un TECHNICIAN solo puede ver órdenes que
   * tiene asignadas; DISPATCHER/SUPERVISOR ven cualquiera. Reutilizado por
   * {@link EvidencePhotoService} (US4) para no duplicar la regla de
   * autorización de {@code GET /orders/{orderId}}.
   */
  Order requireOrderVisibleTo(CurrentUser currentUser, UUID orderId) {
    Order order = findOrderOrThrow(orderId);
    requireVisibleTo(currentUser, order);
    return order;
  }

  private Order findOrderOrThrow(UUID orderId) {
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));
  }

  private void requireVisibleTo(CurrentUser currentUser, Order order) {
    if (currentUser.role() == Role.TECHNICIAN && !isAssignedTo(order, currentUser.id())) {
      throw new ForbiddenException("La orden no está asignada a este technician");
    }
  }

  private boolean isAssignedTo(Order order, UUID technicianId) {
    return order.getAssignedTechnician() != null
        && order.getAssignedTechnician().getId().equals(technicianId);
  }

  private void requireDispatcher(CurrentUser currentUser) {
    // Defensa en profundidad (research.md, STRIDE): no confiar solo en el
    // @PreAuthorize del controller para una operación que crea datos.
    if (currentUser.role() != Role.DISPATCHER) {
      throw new ForbiddenException("Solo un dispatcher puede crear órdenes");
    }
  }
}
