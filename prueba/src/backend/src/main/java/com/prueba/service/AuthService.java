package com.prueba.service;

import com.prueba.dto.LoginResponse;
import com.prueba.exception.UnauthorizedException;
import com.prueba.model.User;
import com.prueba.repository.UserRepository;
import com.prueba.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login por email+password contra los usuarios de seed (FR-024). */
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
