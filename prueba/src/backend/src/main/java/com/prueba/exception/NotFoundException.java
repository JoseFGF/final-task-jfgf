package com.prueba.exception;

/** El recurso solicitado no existe (404). */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
