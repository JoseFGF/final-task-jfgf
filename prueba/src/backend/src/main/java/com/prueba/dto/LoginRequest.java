package com.prueba.dto;

import jakarta.validation.constraints.NotBlank;

/** Body de {@code POST /auth/login} (FR-024), según contracts/openapi.yaml. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
