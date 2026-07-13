package com.prueba.service;

import com.prueba.dto.OrderDetailResponse;
import com.prueba.dto.OrderSummaryResponse;
import com.prueba.exception.ForbiddenException;
import com.prueba.exception.NotFoundException;
import com.prueba.model.Order;
import com.prueba.model.Role;
import com.prueba.repository.OrderRepository;
import com.prueba.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de órdenes visibles para el usuario autenticado (US1: FR-001 a
 * FR-004). TECHNICIAN ve solo las órdenes que tiene asignadas; DISPATCHER y
 * SUPERVISOR ven todas las órdenes del sistema, sin importar estado ni
 * technician asignado.
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

  private final OrderRepository orderRepository;

  public OrderQueryService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  public List<OrderSummaryResponse> listVisibleOrders(CurrentUser currentUser) {
    List<Order> orders =
        currentUser.role() == Role.TECHNICIAN
            ? orderRepository.findByAssignedTechnicianId(currentUser.id())
            : orderRepository.findAll();
    return orders.stream().map(OrderSummaryResponse::from).toList();
  }

  public OrderDetailResponse getOrderDetail(CurrentUser currentUser, UUID orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));
    if (!canView(currentUser, order)) {
      throw new ForbiddenException("No tiene permiso para ver esta orden");
    }
    return OrderDetailResponse.from(order);
  }

  /** FR-004: un TECHNICIAN solo puede ver el detalle de sus propias órdenes asignadas. */
  private boolean canView(CurrentUser currentUser, Order order) {
    if (currentUser.role() != Role.TECHNICIAN) {
      return true;
    }
    return order.getAssignedTechnician() != null
        && order.getAssignedTechnician().getId().equals(currentUser.id());
  }
}
