package com.fieldops.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extrae y valida el JWT del header {@code Authorization: Bearer <token>}
 * (ADR-002, research.md). Solo puebla el {@link org.springframework.security.core.context.SecurityContext}
 * cuando el token es válido; no decide autorización ni maneja errores HTTP
 * aquí (eso corresponde a Spring Security / {@code GlobalExceptionHandler}),
 * para mantener el filtro libre de lógica de negocio.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring("Bearer ".length());
      jwtService
          .parse(token)
          .ifPresent(
              principal -> {
                var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
                var authentication =
                    new UsernamePasswordAuthenticationToken(
                        principal.userId(), null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
              });
    }
    filterChain.doFilter(request, response);
  }
}
