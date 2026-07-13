import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OrderDetail } from './order.model';

const ORDERS_URL = '/api/v1/orders';

/**
 * Servicio de escritura para US4 (Reasignar una orden entre técnicos):
 * consume `POST /api/v1/orders/{orderId}/reassignment` (FR-013, FR-014,
 * FR-021). Solo hace de puente HTTP tipado; las reglas de negocio (rol
 * DISPATCHER, orden no `closed`) las valida el backend.
 */
@Injectable({ providedIn: 'root' })
export class ReassignmentApiService {
  private readonly http = inject(HttpClient);

  /**
   * `POST /api/v1/orders/{orderId}/reassignment`: reasigna la orden al
   * technician indicado por `newTechnicianId` (uuid). Si la orden está en
   * `pending_review`, el backend cambia únicamente el technician responsable
   * sin alterar el estado ni la revisión en curso (FR-013).
   */
  reassignOrder(orderId: string, newTechnicianId: string): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${ORDERS_URL}/${orderId}/reassignment`, { newTechnicianId });
  }
}
