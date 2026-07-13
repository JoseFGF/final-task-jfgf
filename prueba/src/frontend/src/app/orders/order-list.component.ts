import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OrdersQueryApiService } from './orders-query-api.service';
import { OrderSummary } from './order.model';
import { ApiErrorResponse } from '../core/auth.model';

/**
 * US1 — Consultar mis órdenes: lista de órdenes visibles para el usuario
 * autenticado (`GET /api/v1/orders`). El backend decide la visibilidad según
 * el rol (TECHNICIAN ve solo sus órdenes asignadas; DISPATCHER y SUPERVISOR
 * ven todas); este componente solo muestra lo que el backend devuelve.
 */
@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './order-list.component.html',
})
export class OrderListComponent implements OnInit {
  readonly orders = signal<OrderSummary[]>([]);
  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);

  private readonly ordersApi = inject(OrdersQueryApiService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.ordersApi
      .listOrders()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (orders) => {
          this.orders.set(orders);
          this.loading.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.loading.set(false);
          this.errorMessage.set(this.extractErrorMessage(error));
        },
      });
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    const body = error.error as Partial<ApiErrorResponse> | null;
    return body?.message ?? 'No se pudieron cargar las órdenes. Intenta de nuevo.';
  }
}
