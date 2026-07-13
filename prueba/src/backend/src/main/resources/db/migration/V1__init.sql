-- V1__init.sql
-- Esquema inicial de la réplica (data-model.md): users, orders, evidence_photos,
-- access_audit_log. `orders.version` soporta bloqueo optimista para
-- reasignación concurrente (FR-021).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255)  NOT NULL,
    role           VARCHAR(20)   NOT NULL
                       CHECK (role IN ('DISPATCHER', 'TECHNICIAN', 'SUPERVISOR')),
    full_name      VARCHAR(255)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status                   VARCHAR(20) NOT NULL
                                 CHECK (status IN ('draft', 'assigned', 'in_progress', 'pending_review', 'closed')),
    assigned_technician_id   UUID REFERENCES users (id),
    execution_note           TEXT,
    rejection_comment        TEXT,
    last_reassigned_by       UUID REFERENCES users (id),
    last_reassigned_at       TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                  BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_assigned_technician_id ON orders (assigned_technician_id);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE evidence_photos (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES orders (id),
    storage_path  VARCHAR(1024) NOT NULL,
    content_type  VARCHAR(50)   NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    uploaded_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_evidence_photos_order_id ON evidence_photos (order_id);

-- Auditoría de accesos rechazados (FR-023) — tabla append-only, sin borrado.
CREATE TABLE access_audit_log (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempted_by_user_id  UUID REFERENCES users (id),
    attempted_action      VARCHAR(255) NOT NULL,
    reason                VARCHAR(20)  NOT NULL
                              CHECK (reason IN ('NO_SESSION', 'ROLE_NOT_ALLOWED')),
    occurred_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_access_audit_log_attempted_by_user_id ON access_audit_log (attempted_by_user_id);
