import { Component, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { OrderApiService } from './order-api.service';
import { ApiErrorResponse, IncidentSummaryResult } from './order.model';

/**
 * Panel de resumen automático de incidencia vía IA para el supervisor (US5,
 * FR-015/FR-016). Solo tiene sentido a solicitud explícita sobre una orden en
 * `pending_review`; el backend es la autoridad real sobre rol y estado
 * (409/403).
 *
 * Distingue visualmente tres estados para que el supervisor nunca confunda
 * un resumen fiable con la ausencia de evidencia suficiente:
 * - `sufficient: true` → resumen breve, mostrado como texto normal.
 * - `sufficient: false` → aviso explícito de evidencia insuficiente
 *   (banner de aviso, nunca un resumen vacío ni un error).
 * - fallo de red/API → mensaje de error distinto, invitando a reintentar.
 */
@Component({
  selector: 'app-incident-summary',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './incident-summary.component.html',
})
export class IncidentSummaryComponent {
  readonly orderId = input.required<string>();

  readonly loading = signal<boolean>(false);
  readonly result = signal<IncidentSummaryResult | null>(null);
  readonly errorMessage = signal<string | null>(null);

  private readonly orderApi = inject(OrderApiService);

  requestSummary(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.result.set(null);

    this.orderApi.getIncidentSummary(this.orderId()).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage.set(this.extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    const body = error.error as Partial<ApiErrorResponse> | null;
    if (error.status === 403) {
      return body?.message ?? 'No tienes permiso de supervisor para solicitar este resumen.';
    }
    if (error.status === 409) {
      return body?.message ?? 'La orden no está en revisión pendiente o no tiene ejecución registrada.';
    }
    return body?.message ?? 'No se pudo solicitar el resumen de incidencia. Intenta de nuevo.';
  }
}
