import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { OrdersQueryApiService } from './orders-query-api.service';
import { OrderDetail } from './order.model';
import { ApiErrorResponse } from '../core/auth.model';
import { AuthService } from '../core/auth.service';

/**
 * US1 — Consultar mis órdenes: detalle de una orden concreta
 * (`GET /api/v1/orders/{orderId}`). Destaca `rejectionComment` con un banner
 * de aviso cuando existe, para que el usuario vea el motivo del último
 * rechazo (FR-012, FR-022).
 *
 * También ofrece enlaces de navegación a las acciones de las demás
 * historias (ejecución, revisión, reasignación), condicionados por rol y
 * estado de la orden. Este control es solo UX: la autorización real de cada
 * endpoint la aplica siempre el backend.
 */
@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './order-detail.component.html',
})
export class OrderDetailComponent implements OnInit {
  readonly order = signal<OrderDetail | null>(null);
  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);

  private readonly ordersApi = inject(OrdersQueryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly authService = inject(AuthService);

  get canRegisterExecution(): boolean {
    return this.order()?.status === 'in_progress' && this.authService.hasRole('TECHNICIAN');
  }

  get canReview(): boolean {
    return this.order()?.status === 'pending_review' && this.authService.hasRole('SUPERVISOR');
  }

  get canReassign(): boolean {
    const currentOrder = this.order();
    return currentOrder !== null && currentOrder.status !== 'closed' && this.authService.hasRole('DISPATCHER');
  }

  ngOnInit(): void {
    const orderId = this.route.snapshot.paramMap.get('id');
    if (!orderId) {
      this.loading.set(false);
      this.errorMessage.set('Falta el identificador de la orden.');
      return;
    }
    this.loadOrder(orderId);
  }

  private loadOrder(orderId: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.ordersApi
      .getOrderDetail(orderId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => {
          this.order.set(order);
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
    if (error.status === 403) {
      return body?.message ?? 'No tienes permiso para ver esta orden.';
    }
    if (error.status === 404) {
      return body?.message ?? 'La orden no existe.';
    }
    return body?.message ?? 'No se pudo cargar el detalle de la orden.';
  }
}
