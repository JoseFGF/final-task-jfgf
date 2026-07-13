package com.prueba.dto;

import java.util.UUID;

/**
 * Cuerpo de {@code POST /orders/{orderId}/reassignment} (US4: FR-013,
 * FR-014, FR-021).
 */
public record ReassignmentRequest(UUID newTechnicianId) {}
