package com.prueba.exception;

/** Sesión válida pero rol/propiedad del recurso no autorizados (403, FR-018). */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }
}
