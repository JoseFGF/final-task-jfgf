package com.fieldops.dto;

/** Respuesta de {@code POST /auth/login} (FR-024): un JWT listo para usar como Bearer token. */
public record LoginResponse(String token) {}
