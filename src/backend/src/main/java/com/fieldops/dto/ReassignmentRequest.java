package com.fieldops.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de {@code POST /orders/{orderId}/reassignment} (US1, US4), según
 * contracts/openapi.yaml. El technician se identifica por su email (FR-001),
 * no por su UUID interno (ADR-005, research.md).
 */
public record ReassignmentRequest(@NotBlank String newTechnicianEmail) {}
