-- V4__add_order_description.sql
-- Descripción de texto libre del trabajo a realizar (US3, FR-007, FR-007a).
-- Aditiva y nullable (ADR-007, research.md 003): las órdenes de seed
-- existentes quedan con NULL, sin backfill artificial.

ALTER TABLE orders ADD COLUMN description TEXT NULL;
