package com.prueba.exception;

/** El recurso no está en el estado requerido para la operación solicitada (409). */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
