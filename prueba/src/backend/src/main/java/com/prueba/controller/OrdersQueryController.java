package com.prueba.controller;

import com.prueba.dto.OrderDetailResponse;
import com.prueba.dto.OrderSummaryResponse;
import com.prueba.security.CurrentUser;
import com.prueba.service.OrderQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de consulta de órdenes de `contracts/openapi.yaml` — US1
 * (FR-001 a FR-004): {@code GET /orders} y {@code GET /orders/{orderId}}.
 * Solo lectura: registro de ejecución (US2), revisión (US3) y reasignación
 * (US4) viven en otros controllers, fuera de esta clase.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrdersQueryController {

  private final OrderQueryService orderQueryService;

  public OrdersQueryController(OrderQueryService orderQueryService) {
    this.orderQueryService = orderQueryService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('DISPATCHER', 'TECHNICIAN', 'SUPERVISOR')")
  public List<OrderSummaryResponse> listOrders(Authentication authentication) {
    return orderQueryService.listVisibleOrders(CurrentUser.from(authentication));
  }

  @GetMapping("/{orderId}")
  @PreAuthorize("hasAnyRole('DISPATCHER', 'TECHNICIAN', 'SUPERVISOR')")
  public OrderDetailResponse getOrder(Authentication authentication, @PathVariable UUID orderId) {
    return orderQueryService.getOrderDetail(CurrentUser.from(authentication), orderId);
  }
}
