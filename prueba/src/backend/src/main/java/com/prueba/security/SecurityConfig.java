package com.prueba.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de Spring Security: sesiones stateless (JWT),
 * {@code @EnableMethodSecurity} para que los controllers de negocio (fuera
 * de esta base común) usen {@code @PreAuthorize} por rol, y 401/403
 * diferenciados y auditados (FR-017/018, FR-023).
 *
 * <p>Esta réplica corre sobre HTTP plano en local: no hay perfil `prod` ni
 * configuración HTTPS/TLS.
 *
 * <p>{@code POST /api/v1/auth/login} (FR-024) es el único endpoint público:
 * no requiere sesión previa porque su propósito es precisamente emitir una.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private static final String LOGIN_PATH = "/api/v1/auth/login";

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtAuthFilter jwtAuthFilter,
      JwtAuthenticationEntryPoint authenticationEntryPoint,
      JwtAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(LOGIN_PATH).permitAll().anyRequest().authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
