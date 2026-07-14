package com.fieldops.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de {@code POST /orders} (US3): {@code description} es obligatoria
 * (FR-007a, validada además tras recortar espacios en el servicio);
 * {@code technicianEmail} es opcional (FR-007b).
 */
public record CreateOrderRequest(@NotBlank String description, String technicianEmail) {}
