package com.prueba.controller;

import com.prueba.dto.OrderDetailResponse;
import com.prueba.security.CurrentUser;
import com.prueba.service.ExecutionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint de registro de ejecución de `contracts/openapi.yaml` (US2:
 * FR-005, FR-006, FR-006a, FR-007, FR-008): {@code POST
 * /orders/{orderId}/execution}. Consulta de órdenes (US1), revisión (US3) y
 * reasignación (US4) viven en otros controllers.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class ExecutionController {

  private final ExecutionService executionService;

  public ExecutionController(ExecutionService executionService) {
    this.executionService = executionService;
  }

  @PostMapping(value = "/{orderId}/execution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('TECHNICIAN')")
  public OrderDetailResponse registerExecution(
      Authentication authentication,
      @PathVariable UUID orderId,
      @RequestParam String note,
      @RequestParam(required = false) List<MultipartFile> photos) {
    return executionService.registerExecution(
        CurrentUser.from(authentication), orderId, note, photos);
  }
}
