package com.fieldops.service;

import com.fieldops.dto.LoginResponse;
import com.fieldops.exception.UnauthorizedException;
import com.fieldops.model.User;
import com.fieldops.repository.UserRepository;
import com.fieldops.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login por email+password contra los usuarios de seed (FR-024): hueco
 * detectado durante `/speckit.implement` (no había forma de que un usuario
 * real obtuviera un JWT; solo los tests lo generaban vía {@link JwtService}
 * directamente). No forma parte de ninguna user story original de
 * `spec.md` — ver Assumptions.
 */
@Service
public class AuthService {

  private static final String INVALID_CREDENTIALS_MESSAGE = "Email o contraseña incorrectos";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(
      UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional(readOnly = true)
  public LoginResponse login(String email, String password) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
    }
    return new LoginResponse(jwtService.generateToken(user.getId(), user.getRole()));
  }
}
