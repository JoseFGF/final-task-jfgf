import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OrderDetail, OrderSummary } from './order.model';

const ORDERS_URL = '/api/v1/orders';

/**
 * Servicio de solo-lectura para US1 (Consultar mis órdenes): consume
 * `GET /api/v1/orders` y `GET /api/v1/orders/{orderId}`. El backend aplica
 * las reglas de visibilidad por rol (TECHNICIAN ve solo sus órdenes
 * asignadas; DISPATCHER y SUPERVISOR ven todas) — este servicio solo hace de
 * puente HTTP tipado, sin lógica de negocio adicional.
 */
@Injectable({ providedIn: 'root' })
export class OrdersQueryApiService {
  private readonly http = inject(HttpClient);

  /** `GET /api/v1/orders`: lista de órdenes visibles para el usuario autenticado. */
  listOrders(): Observable<OrderSummary[]> {
    return this.http.get<OrderSummary[]>(ORDERS_URL);
  }

  /** `GET /api/v1/orders/{orderId}`: detalle de una orden concreta. */
  getOrderDetail(orderId: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${ORDERS_URL}/${orderId}`);
  }
}
