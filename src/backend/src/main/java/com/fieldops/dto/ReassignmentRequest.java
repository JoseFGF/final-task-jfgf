package com.fieldops.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Body de {@code POST /orders/{orderId}/reassignment} (US4), según
 * contracts/openapi.yaml.
 */
public record ReassignmentRequest(@NotNull UUID newTechnicianId) {}
