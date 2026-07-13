/**
 * Estados posibles de una orden (contracts/openapi.yaml, `OrderStatus`).
 */
export type OrderStatus = 'draft' | 'assigned' | 'in_progress' | 'pending_review' | 'closed';

/**
 * Resumen de orden devuelto por `GET /api/v1/orders` (`OrderSummary`).
 */
export interface OrderSummary {
  id: string;
  status: OrderStatus;
  assignedTechnicianId: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Detalle de orden devuelto por `GET /api/v1/orders/{orderId}` (`OrderDetail`).
 *
 * Incluye `rejectionComment`, que otras historias (US2 ejecución) también
 * consumen para mostrar el motivo del último rechazo antes de reenviar.
 */
export interface OrderDetail extends OrderSummary {
  executionNote: string | null;
  evidencePhotoIds: string[];
  /** Motivo del último rechazo (FR-012, FR-022); null si nunca fue rechazada o ya fue aprobada. */
  rejectionComment: string | null;
}
