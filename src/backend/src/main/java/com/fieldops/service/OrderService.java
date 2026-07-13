package com.fieldops.service;

import com.fieldops.dto.OrderDetailResponse;
import com.fieldops.dto.OrderSummaryResponse;
import com.fieldops.exception.ForbiddenException;
import com.fieldops.exception.NotFoundException;
import com.fieldops.model.Order;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de órdenes (US1): visibilidad por rol (FR-001 a FR-003) y rechazo
 * de acceso al detalle de una orden ajena (FR-004). TECHNICIAN ve solo sus
 * órdenes asignadas; DISPATCHER y SUPERVISOR ven todas las órdenes del
 * sistema, sin distinción de estado.
 */
@Service
public class OrderService {

  private final OrderRepository orderRepository;

  public OrderService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
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
    Order order = findOrderOrThrow(orderId);
    requireVisibleTo(currentUser, order);
    return OrderDetailResponse.from(order);
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
}
