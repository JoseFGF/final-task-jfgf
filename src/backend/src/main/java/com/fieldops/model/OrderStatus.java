package com.fieldops.model;

/** Estados del ciclo de vida de una orden (data-model.md). */
public enum OrderStatus {
  draft,
  assigned,
  in_progress,
  pending_review,
  closed
}
