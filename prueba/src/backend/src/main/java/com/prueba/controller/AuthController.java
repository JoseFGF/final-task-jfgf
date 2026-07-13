package com.prueba.controller;

import com.prueba.dto.LoginRequest;
import com.prueba.dto.LoginResponse;
import com.prueba.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de login de `contracts/openapi.yaml` (FR-024): único endpoint
 * público de la API (ver {@code SecurityConfig}), sin {@code @PreAuthorize}
 * porque su propósito es precisamente emitir la sesión que el resto de
 * endpoints exige.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request.email(), request.password());
  }
}
