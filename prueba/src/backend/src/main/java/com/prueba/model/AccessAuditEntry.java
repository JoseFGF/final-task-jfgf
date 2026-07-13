package com.prueba.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Auditoría append-only de accesos rechazados (FR-023, data-model.md).
 * `attemptedByUser` es nulo si no había sesión válida (401 -> NO_SESSION);
 * cuando hay sesión pero rol incorrecto (403), el motivo es ROLE_NOT_ALLOWED.
 */
@Entity
@Table(name = "access_audit_log")
public class AccessAuditEntry {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attempted_by_user_id")
  private User attemptedByUser;

  @Column(name = "attempted_action", nullable = false)
  private String attemptedAction;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccessDenialReason reason;

  @CreationTimestamp
  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected AccessAuditEntry() {}

  public AccessAuditEntry(
      UUID id, User attemptedByUser, String attemptedAction, AccessDenialReason reason) {
    this.id = id;
    this.attemptedByUser = attemptedByUser;
    this.attemptedAction = attemptedAction;
    this.reason = reason;
  }

  public UUID getId() {
    return id;
  }

  public User getAttemptedByUser() {
    return attemptedByUser;
  }

  public String getAttemptedAction() {
    return attemptedAction;
  }

  public AccessDenialReason getReason() {
    return reason;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
