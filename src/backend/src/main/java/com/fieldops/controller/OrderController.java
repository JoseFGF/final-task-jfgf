package com.fieldops.controller;

import com.fieldops.dto.CreateOrderRequest;
import com.fieldops.dto.IncidentSummaryResponse;
import com.fieldops.dto.OrderDetailResponse;
import com.fieldops.dto.OrderSummaryResponse;
import com.fieldops.dto.ReassignmentRequest;
import com.fieldops.dto.ReviewRequest;
import com.fieldops.security.CurrentUser;
import com.fieldops.service.EvidencePhotoService;
import com.fieldops.service.EvidencePhotoService.PhotoContent;
import com.fieldops.service.ExecutionService;
import com.fieldops.service.IncidentSummaryService;
import com.fieldops.service.OrderService;
import com.fieldops.service.ReassignmentService;
import com.fieldops.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints de órdenes de `contracts/openapi.yaml`. Este controller se
 * amplía en fases futuras (review, reassignment, incident-summary — US3 a
 * US5): cada rol autorizado se declara explícitamente aquí vía
 * {@code @PreAuthorize}, nunca por omisión (Principio II).
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

  private final OrderService orderService;
  private final ExecutionService executionService;
  private final ReviewService reviewService;
  private final ReassignmentService reassignmentService;
  private final IncidentSummaryService incidentSummaryService;
  private final EvidencePhotoService evidencePhotoService;

  public OrderController(
      OrderService orderService,
      ExecutionService executionService,
      ReviewService reviewService,
      ReassignmentService reassignmentService,
      IncidentSummaryService incidentSummaryService,
      EvidencePhotoService evidencePhotoService) {
    this.orderService = orderService;
    this.executionService = executionService;
    this.reviewService = reviewService;
    this.reassignmentService = reassignmentService;
    this.incidentSummaryService = incidentSummaryService;
    this.evidencePhotoService = evidencePhotoService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('DISPATCHER', 'TECHNICIAN', 'SUPERVISOR')")
  public List<OrderSummaryResponse> listOrders(Authentication authentication) {
    return orderService.listVisibleOrders(CurrentUser.from(authentication));
  }

  @PostMapping
  @PreAuthorize("hasRole('DISPATCHER')")
  public ResponseEntity<OrderDetailResponse> createOrder(
      @Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
    OrderDetailResponse created = orderService.createOrder(CurrentUser.from(authentication), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/{orderId}")
  @PreAuthorize("hasAnyRole('DISPATCHER', 'TECHNICIAN', 'SUPERVISOR')")
  public OrderDetailResponse getOrder(
      @PathVariable UUID orderId, Authentication authentication) {
    return orderService.getOrderDetail(CurrentUser.from(authentication), orderId);
  }

  @GetMapping("/{orderId}/evidence-photos/{photoId}")
  @PreAuthorize("hasAnyRole('DISPATCHER', 'TECHNICIAN', 'SUPERVISOR')")
  public ResponseEntity<Resource> getEvidencePhoto(
      @PathVariable UUID orderId, @PathVariable UUID photoId, Authentication authentication) {
    PhotoContent photo =
        evidencePhotoService.getPhotoContent(CurrentUser.from(authentication), orderId, photoId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, photo.contentType())
        .body(photo.resource());
  }

  @PostMapping(path = "/{orderId}/execution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('TECHNICIAN')")
  public OrderDetailResponse registerExecution(
      @PathVariable UUID orderId,
      @RequestParam("note") String note,
      @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
      Authentication authentication) {
    return executionService.registerExecution(
        CurrentUser.from(authentication), orderId, note, photos);
  }

  @PostMapping("/{orderId}/review")
  @PreAuthorize("hasRole('SUPERVISOR')")
  public OrderDetailResponse reviewOrder(
      @PathVariable UUID orderId,
      @Valid @RequestBody ReviewRequest request,
      Authentication authentication) {
    CurrentUser currentUser = CurrentUser.from(authentication);
    return switch (request.decision()) {
      case APPROVE -> reviewService.approve(currentUser, orderId);
      case REJECT -> reviewService.reject(currentUser, orderId, request.comment());
    };
  }

  @PostMapping("/{orderId}/reassignment")
  @PreAuthorize("hasRole('DISPATCHER')")
  public OrderDetailResponse reassignOrder(
      @PathVariable UUID orderId,
      @Valid @RequestBody ReassignmentRequest request,
      Authentication authentication) {
    return reassignmentService.reassign(
        CurrentUser.from(authentication), orderId, request.newTechnicianEmail());
  }

  @PostMapping("/{orderId}/incident-summary")
  @PreAuthorize("hasRole('SUPERVISOR')")
  public IncidentSummaryResponse getIncidentSummary(
      @PathVariable UUID orderId, Authentication authentication) {
    return incidentSummaryService.summarize(CurrentUser.from(authentication), orderId);
  }
}
