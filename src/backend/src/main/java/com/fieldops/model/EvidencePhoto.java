package com.fieldops.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Metadata de una foto de evidencia; la imagen en sí vive en el volumen
 * Docker (ADR-004), no en la base de datos.
 */
@Entity
@Table(name = "evidence_photos")
public class EvidencePhoto {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "storage_path", nullable = false, length = 1024)
  private String storagePath;

  @Column(name = "content_type", nullable = false, length = 50)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @CreationTimestamp
  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  protected EvidencePhoto() {}

  public EvidencePhoto(UUID id, Order order, String storagePath, String contentType, long sizeBytes) {
    this.id = id;
    this.order = order;
    this.storagePath = storagePath;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
  }

  public UUID getId() {
    return id;
  }

  public Order getOrder() {
    return order;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }
}
