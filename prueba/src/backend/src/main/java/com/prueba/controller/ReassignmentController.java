package com.prueba.controller;

import com.prueba.dto.OrderDetailResponse;
import com.prueba.dto.ReassignmentRequest;
import com.prueba.security.CurrentUser;
import com.prueba.service.ReassignmentService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de reasignación de `contracts/openapi.yaml` (US4: FR-013, FR-014,
 * FR-021): {@code POST /orders/{orderId}/reassignment}. Consulta de órdenes
 * (US1), ejecución (US2) y revisión (US3) viven en otros controllers.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class ReassignmentController {

  private final ReassignmentService reassignmentService;

  public ReassignmentController(ReassignmentService reassignmentService) {
    this.reassignmentService = reassignmentService;
  }

  @PostMapping("/{orderId}/reassignment")
  @PreAuthorize("hasRole('DISPATCHER')")
  public OrderDetailResponse reassign(
      Authentication authentication,
      @PathVariable UUID orderId,
      @RequestBody ReassignmentRequest request) {
    return reassignmentService.reassign(CurrentUser.from(authentication), orderId, request);
  }
}
