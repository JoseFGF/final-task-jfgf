package com.prueba.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Escribe el cuerpo JSON {@link ErrorResponse} para los rechazos que ocurren
 * a nivel de filtro de Spring Security (antes de llegar al
 * {@code DispatcherServlet}), donde {@code GlobalExceptionHandler} no puede
 * intervenir.
 */
@Component
public class SecurityErrorResponseWriter {

  private final ObjectMapper objectMapper;

  public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(code, message)));
  }
}
