package com.prueba.exception;

/** Credenciales de login inválidas o inexistentes (401, FR-024). */
public class UnauthorizedException extends RuntimeException {

  public UnauthorizedException(String message) {
    super(message);
  }
}
