/**
 * Modelos de orden alineados con `contracts/openapi.yaml`
 * (`OrderStatus`, `OrderSummary`, `OrderDetail`).
 */
export type OrderStatus = 'draft' | 'assigned' | 'in_progress' | 'pending_review' | 'closed';

export interface OrderSummary {
  id: string;
  status: OrderStatus;
  assignedTechnicianId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface OrderDetail extends OrderSummary {
  executionNote: string | null;
  evidencePhotoIds: string[];
  rejectionComment: string | null;
}

/** Decisión de revisión de un supervisor (`POST /orders/{orderId}/review`). */
export type ReviewDecision = 'APPROVE' | 'REJECT';

/** Forma del `ErrorResponse` devuelto por el backend (`contracts/openapi.yaml`). */
export interface ApiErrorResponse {
  code: string;
  message: string;
}

/**
 * Resultado de `POST /orders/{orderId}/incident-summary` (US5, FR-015/FR-016).
 * Cuando `sufficient` es `false`, `summary` es siempre `null`: el backend
 * declara explícitamente evidencia insuficiente en vez de inventar contenido
 * (nunca se debe tratar como un resumen vacío ni como un error).
 */
export interface IncidentSummaryResult {
  sufficient: boolean;
  summary: string | null;
}
