package com.prueba.controller;

import com.prueba.dto.OrderDetailResponse;
import com.prueba.dto.ReviewRequest;
import com.prueba.security.CurrentUser;
import com.prueba.service.ReviewService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de revisión de `contracts/openapi.yaml` (US3: FR-009 a FR-012a):
 * {@code POST /orders/{orderId}/review}. Consulta de órdenes (US1), ejecución
 * (US2) y reasignación (US4) viven en otros controllers.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class ReviewController {

  private final ReviewService reviewService;

  public ReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @PostMapping("/{orderId}/review")
  @PreAuthorize("hasRole('SUPERVISOR')")
  public OrderDetailResponse review(
      Authentication authentication,
      @PathVariable UUID orderId,
      @RequestBody ReviewRequest request) {
    return reviewService.review(CurrentUser.from(authentication), orderId, request);
  }
}
