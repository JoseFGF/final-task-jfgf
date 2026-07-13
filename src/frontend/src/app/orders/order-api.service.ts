import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IncidentSummaryResult, OrderDetail, OrderSummary, ReviewDecision } from './order.model';

const ORDERS_BASE_URL = '/api/v1/orders';

/**
 * Cliente HTTP para el recurso `orders` (`contracts/openapi.yaml`). No aplica
 * ninguna lógica de visibilidad por rol propia: el backend ya filtra qué
 * órdenes devuelve según el rol del JWT (FR-001/002/003); este servicio solo
 * expone lo que la API responde.
 */
@Injectable({ providedIn: 'root' })
export class OrderApiService {
  private readonly http = inject(HttpClient);

  /** `GET /orders` — órdenes visibles para el usuario autenticado. */
  listOrders(): Observable<OrderSummary[]> {
    return this.http.get<OrderSummary[]>(ORDERS_BASE_URL);
  }

  /** `GET /orders/{orderId}` — detalle de una orden. */
  getOrderDetail(orderId: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${ORDERS_BASE_URL}/${orderId}`);
  }

  /**
   * `POST /orders/{orderId}/execution` (multipart/form-data). Requiere al
   * menos una foto (FR-006); el backend es la autoridad final sobre esta
   * validación, aunque el formulario también la exija por UX.
   */
  registerExecution(orderId: string, note: string, photos: readonly File[]): Observable<OrderDetail> {
    const formData = new FormData();
    formData.append('note', note);
    photos.forEach((photo) => formData.append('photos', photo, photo.name));

    return this.http.post<OrderDetail>(`${ORDERS_BASE_URL}/${orderId}/execution`, formData);
  }

  /**
   * `POST /orders/{orderId}/review` — aprobar o rechazar una orden en
   * `pending_review` (FR-009 a FR-012a). `comment` es obligatorio y no vacío
   * cuando `decision = REJECT`; el backend es la autoridad final sobre esta
   * validación (FR-012a).
   */
  reviewOrder(orderId: string, decision: ReviewDecision, comment?: string): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${ORDERS_BASE_URL}/${orderId}/review`, {
      decision,
      ...(comment !== undefined ? { comment } : {}),
    });
  }

  /**
   * `POST /orders/{orderId}/reassignment` — reasigna la orden a otro
   * technician (FR-013, FR-014). Solo válida sobre órdenes `assigned`,
   * `in_progress` o `pending_review`; el backend es la autoridad final sobre
   * el rol (DISPATCHER) y el estado de la orden (FR-014).
   */
  reassignOrder(orderId: string, newTechnicianId: string): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${ORDERS_BASE_URL}/${orderId}/reassignment`, {
      newTechnicianId,
    });
  }

  /**
   * `POST /orders/{orderId}/incident-summary` — resumen automático de la
   * incidencia vía IA (US5, FR-015/FR-016). Solo tiene sentido para una orden
   * `pending_review`; el backend es la autoridad final sobre el rol
   * (SUPERVISOR) y el estado de la orden (409 en caso contrario). La
   * respuesta 200 con `sufficient: false` no es un error: es el propio
   * mecanismo fail-safe (ADR-001) que declara evidencia insuficiente en vez
   * de inventar contenido.
   */
  getIncidentSummary(orderId: string): Observable<IncidentSummaryResult> {
    return this.http.post<IncidentSummaryResult>(
      `${ORDERS_BASE_URL}/${orderId}/incident-summary`,
      {},
    );
  }
}
