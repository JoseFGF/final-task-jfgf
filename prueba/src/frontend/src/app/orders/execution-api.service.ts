import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OrderDetail } from './order.model';

const ORDERS_URL = '/api/v1/orders';

/**
 * Servicio de escritura para US2 (Registrar la ejecución de una orden):
 * consume `POST /api/v1/orders/{orderId}/execution` (multipart/form-data)
 * (FR-005, FR-006, FR-007, FR-008). Solo hace de puente HTTP tipado; las
 * reglas de negocio (rol TECHNICIAN, orden `in_progress`, foto obligatoria)
 * las valida el backend.
 */
@Injectable({ providedIn: 'root' })
export class ExecutionApiService {
  private readonly http = inject(HttpClient);

  /**
   * `POST /api/v1/orders/{orderId}/execution`: registra la nota y las fotos
   * de evidencia de la ejecución. Requiere al menos una foto (FR-006); la
   * orden pasa a `pending_review` si la petición se acepta.
   */
  registerExecution(orderId: string, note: string, photos: readonly File[]): Observable<OrderDetail> {
    const formData = new FormData();
    formData.append('note', note);
    for (const photo of photos) {
      formData.append('photos', photo, photo.name);
    }
    return this.http.post<OrderDetail>(`${ORDERS_URL}/${orderId}/execution`, formData);
  }
}
