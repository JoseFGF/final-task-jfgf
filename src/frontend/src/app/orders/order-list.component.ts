import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { OrderApiService } from './order-api.service';
import { OrderSummary } from './order.model';
import { AuthService } from '../core/auth.service';

/**
 * Lista de órdenes visibles para el usuario autenticado (US1, FR-001 a
 * FR-003). El backend ya filtra por rol — este componente solo muestra lo
 * que `GET /orders` devuelve.
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

  private readonly orderApi = inject(OrderApiService);
  private readonly authService = inject(AuthService);

  /** `true` si el usuario autenticado es DISPATCHER (US3): solo él puede crear órdenes. */
  get canCreateOrder(): boolean {
    return this.authService.hasRole('DISPATCHER');
  }

  ngOnInit(): void {
    this.orderApi.listOrders().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('No se pudieron cargar las órdenes. Intenta de nuevo más tarde.');
        this.loading.set(false);
      },
    });
  }

  statusLabel(status: OrderSummary['status']): string {
    const labels: Record<OrderSummary['status'], string> = {
      draft: 'Borrador',
      assigned: 'Asignada',
      in_progress: 'En progreso',
      pending_review: 'Pendiente de revisión',
      closed: 'Cerrada',
    };
    return labels[status];
  }
}
