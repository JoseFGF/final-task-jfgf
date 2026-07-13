import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OrderDetail } from './order.model';

const ORDERS_URL = '/api/v1/orders';

/** Decisión de revisión aceptada por `POST /api/v1/orders/{orderId}/review`. */
export type ReviewDecision = 'APPROVE' | 'REJECT';

/**
 * Servicio de escritura para US3 (Aprobar o rechazar una orden en revisión):
 * consume `POST /api/v1/orders/{orderId}/review` (FR-009 a FR-012a). Solo
 * hace de puente HTTP tipado; las reglas de negocio (rol SUPERVISOR, orden
 * `pending_review`, comentario obligatorio al rechazar) las valida el
 * backend.
 */
@Injectable({ providedIn: 'root' })
export class ReviewApiService {
  private readonly http = inject(HttpClient);

  /**
   * `POST /api/v1/orders/{orderId}/review`: aprueba (la orden pasa a
   * `closed`) o rechaza (la orden vuelve a `in_progress`, guardando
   * `comment` como `rejectionComment`, FR-012, FR-022) una orden en
   * `pending_review`. `comment` es obligatorio y no vacío cuando
   * `decision === 'REJECT'` (FR-012a).
   */
  reviewOrder(orderId: string, decision: ReviewDecision, comment?: string): Observable<OrderDetail> {
    const body: { decision: ReviewDecision; comment?: string } = { decision };
    if (comment !== undefined) {
      body.comment = comment;
    }
    return this.http.post<OrderDetail>(`${ORDERS_URL}/${orderId}/review`, body);
  }
}
