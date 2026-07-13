package com.fieldops.security;

import com.fieldops.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Emisión y validación de JWT stateless con el rol del usuario como claim
 * (ADR-002, research.md). Esta fase solo necesita validar tokens ya
 * emitidos; {@link #generateToken(UUID, Role)} existe como contraparte
 * simétrica de {@link #parse(String)} para que tests y una futura historia
 * de login reutilicen la misma lógica de firma, sin implementar aquí
 * login/registro de usuarios.
 */
@Component
public class JwtService {

  private final SecretKey signingKey;
  private final long expirationSeconds;

  public JwtService(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration-seconds:3600}") long expirationSeconds) {
    this.signingKey = deriveKey(secret);
    this.expirationSeconds = expirationSeconds;
  }

  public String generateToken(UUID userId, Role role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .claim("role", role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expirationSeconds)))
        .signWith(signingKey)
        .compact();
  }

  public Optional<AuthenticatedPrincipal> parse(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      UUID userId = UUID.fromString(claims.getSubject());
      Role role = Role.valueOf(claims.get("role", String.class));
      return Optional.of(new AuthenticatedPrincipal(userId, role));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Deriva una clave HMAC de 256 bits a partir del secreto configurado, sea cual sea su longitud. */
  private static SecretKey deriveKey(String secret) {
    try {
      byte[] hashed = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
      return Keys.hmacShaKeyFor(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible en este JDK", e);
    }
  }

  public record AuthenticatedPrincipal(UUID userId, Role role) {}
}
