import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OrderApiService } from './order-api.service';
import { OrderDetail } from './order.model';
import { AuthService } from '../core/auth.service';

/**
 * Detalle de una orden (US1, FR-001 a FR-004). Destaca el
 * `rejectionComment` cuando existe (FR-022), para que el technician sepa qué
 * corregir antes de reenviar la ejecución.
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

  private orderId = '';

  private readonly route = inject(ActivatedRoute);
  private readonly orderApi = inject(OrderApiService);
  private readonly authService = inject(AuthService);

  /**
   * `true` si al usuario actual (rol SUPERVISOR) le corresponde revisar esta
   * orden, es decir, está en `pending_review`. Es un control de UX
   * (Principio II): el backend es quien valida realmente el rol y el estado
   * (FR-010) al recibir `POST /orders/{orderId}/review`.
   */
  readonly canReview = computed<boolean>(() => {
    const currentOrder = this.order();
    return (
      currentOrder !== null &&
      currentOrder.status === 'pending_review' &&
      this.authService.hasRole('SUPERVISOR')
    );
  });

  /**
   * `true` si al usuario actual (rol DISPATCHER) le corresponde poder
   * reasignar esta orden, es decir, no está `closed` (FR-014). Es un
   * control de UX (Principio II): el backend es quien valida realmente el
   * rol y el estado (FR-014) al recibir `POST /orders/{orderId}/reassignment`.
   */
  readonly canReassign = computed<boolean>(() => {
    const currentOrder = this.order();
    return (
      currentOrder !== null &&
      currentOrder.status !== 'closed' &&
      this.authService.hasRole('DISPATCHER')
    );
  });

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('id') ?? '';
    this.loadOrder();
  }

  loadOrder(): void {
    if (!this.orderId) {
      this.errorMessage.set('Orden no encontrada.');
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.orderApi.getOrderDetail(this.orderId).subscribe({
      next: (order) => {
        this.order.set(order);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('No se pudo cargar el detalle de la orden.');
        this.loading.set(false);
      },
    });
  }
}
