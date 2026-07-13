package com.fieldops.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Orden de trabajo. La transición de estados vive en el servicio de negocio
 * (fuera de esta fase); esta entidad solo modela los datos (data-model.md).
 * `version` soporta bloqueo optimista para reasignación concurrente (FR-021).
 */
@Entity
@Table(name = "orders")
public class Order {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_technician_id")
  private User assignedTechnician;

  @Column(name = "execution_note", columnDefinition = "text")
  private String executionNote;

  @Column(name = "rejection_comment", columnDefinition = "text")
  private String rejectionComment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "last_reassigned_by")
  private User lastReassignedBy;

  @Column(name = "last_reassigned_at")
  private Instant lastReassignedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<EvidencePhoto> evidencePhotos = new ArrayList<>();

  protected Order() {}

  public Order(UUID id, OrderStatus status, User assignedTechnician) {
    this.id = id;
    this.status = status;
    this.assignedTechnician = assignedTechnician;
  }

  public UUID getId() {
    return id;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public void setStatus(OrderStatus status) {
    this.status = status;
  }

  public User getAssignedTechnician() {
    return assignedTechnician;
  }

  public void setAssignedTechnician(User assignedTechnician) {
    this.assignedTechnician = assignedTechnician;
  }

  public String getExecutionNote() {
    return executionNote;
  }

  public void setExecutionNote(String executionNote) {
    this.executionNote = executionNote;
  }

  public String getRejectionComment() {
    return rejectionComment;
  }

  public void setRejectionComment(String rejectionComment) {
    this.rejectionComment = rejectionComment;
  }

  public User getLastReassignedBy() {
    return lastReassignedBy;
  }

  public void setLastReassignedBy(User lastReassignedBy) {
    this.lastReassignedBy = lastReassignedBy;
  }

  public Instant getLastReassignedAt() {
    return lastReassignedAt;
  }

  public void setLastReassignedAt(Instant lastReassignedAt) {
    this.lastReassignedAt = lastReassignedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  public List<EvidencePhoto> getEvidencePhotos() {
    return evidencePhotos;
  }
}
