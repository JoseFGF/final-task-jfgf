package com.fieldops.exception;

/** La petición es semánticamente inválida (422) — p.ej. falta evidencia obligatoria. */
public class ValidationException extends RuntimeException {

  public ValidationException(String message) {
    super(message);
  }
}
