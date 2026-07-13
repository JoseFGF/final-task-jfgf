package com.fieldops.security;

import com.fieldops.model.AccessDenialReason;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Responde 403 cuando la sesión es válida pero el rol no corresponde
 * (FR-018), a nivel de filtro (reglas de {@code authorizeHttpRequests}).
 * Los rechazos por {@code @PreAuthorize} en un controller se resuelven en
 * {@code GlobalExceptionHandler}, no aquí (ver su Javadoc).
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

  private final AccessAuditAspect accessAuditAspect;
  private final SecurityErrorResponseWriter responseWriter;

  public JwtAccessDeniedHandler(
      AccessAuditAspect accessAuditAspect, SecurityErrorResponseWriter responseWriter) {
    this.accessAuditAspect = accessAuditAspect;
    this.responseWriter = responseWriter;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    accessAuditAspect.recordDenied(request, AccessDenialReason.ROLE_NOT_ALLOWED);
    responseWriter.write(
        response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Rol no autorizado para esta acción");
  }
}
