package com.fieldops.service;

import com.fieldops.exception.ValidationException;
import com.fieldops.model.Role;
import com.fieldops.model.User;
import com.fieldops.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Resuelve un technician por email, insensible a mayúsculas/minúsculas y
 * recortando espacios (FR-002, FR-003, ADR-005). Rechaza tanto un email
 * inexistente como uno que exista con un rol distinto de TECHNICIAN.
 *
 * <p>Colaborador compartido por {@link ReassignmentService} (asignación
 * inicial/reasignación) y {@link OrderService} (creación de órdenes con
 * technician indicado, FR-007b): antes vivía como método estático en
 * {@code ReassignmentService}, invocado por {@code OrderService} como si
 * fuera una utility class — se extrae aquí para que ambos lo reciban por
 * inyección de constructor, como el resto de colaboradores del proyecto
 * (code review, feature 003).
 */
@Service
public class TechnicianLookupService {

  private final UserRepository userRepository;

  public TechnicianLookupService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User resolveByEmail(String email) {
    User technician =
        userRepository
            .findByEmailIgnoreCase(email.strip())
            .orElseThrow(
                () ->
                    new ValidationException(
                        "El email no corresponde a ningún technician válido: " + email));
    if (technician.getRole() != Role.TECHNICIAN) {
      throw new ValidationException(
          "El email no corresponde a ningún technician válido: " + email);
    }
    return technician;
  }
}
