package com.prueba.security;

import com.prueba.model.AccessDenialReason;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Responde 401 cuando no hay sesión válida (FR-017) y registra la auditoría (FR-023). */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final AccessAuditAspect accessAuditAspect;
  private final SecurityErrorResponseWriter responseWriter;

  public JwtAuthenticationEntryPoint(
      AccessAuditAspect accessAuditAspect, SecurityErrorResponseWriter responseWriter) {
    this.accessAuditAspect = accessAuditAspect;
    this.responseWriter = responseWriter;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    accessAuditAspect.recordDenied(request, AccessDenialReason.NO_SESSION);
    responseWriter.write(
        response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "No hay sesión válida");
  }
}
