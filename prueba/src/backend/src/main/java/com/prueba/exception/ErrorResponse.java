package com.prueba.exception;

/** DTO de error consistente con el schema `ErrorResponse` de contracts/openapi.yaml. */
public record ErrorResponse(String code, String message) {}
