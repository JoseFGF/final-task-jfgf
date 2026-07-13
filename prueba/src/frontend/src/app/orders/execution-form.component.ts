import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ExecutionApiService } from './execution-api.service';
import { OrdersQueryApiService } from './orders-query-api.service';
import { OrderDetail } from './order.model';
import { ApiErrorResponse } from '../core/auth.model';

interface ExecutionFormValue {
  note: FormControl<string>;
}

/**
 * US2 — Registrar la ejecución de una orden
 * (`POST /api/v1/orders/{orderId}/execution`, FR-005 a FR-008). Solo el
 * TECHNICIAN asignado y con la orden en `in_progress` puede completar este
 * formulario (autorización real la aplica el backend); requiere al menos
 * una foto de evidencia (FR-006) y muestra el `rejectionComment` previo, si
 * lo hay, para que el técnico sepa qué corregir antes de reenviar (FR-022).
 */
@Component({
  selector: 'app-execution-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './execution-form.component.html',
})
export class ExecutionFormComponent implements OnInit {
  readonly form = new FormGroup<ExecutionFormValue>({
    note: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly order = signal<OrderDetail | null>(null);
  readonly loadingOrder = signal<boolean>(true);
  readonly photos = signal<readonly File[]>([]);
  readonly submitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly submitted = signal<boolean>(false);

  private readonly executionApi = inject(ExecutionApiService);
  private readonly ordersApi = inject(OrdersQueryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private orderId: string | null = null;

  get canSubmit(): boolean {
    return this.form.valid && this.photos().length > 0 && !this.submitting();
  }

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('id');
    if (!this.orderId) {
      this.loadingOrder.set(false);
      this.errorMessage.set('Falta el identificador de la orden.');
      return;
    }
    this.loadOrder(this.orderId);
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.photos.set(input.files ? Array.from(input.files) : []);
  }

  submit(): void {
    if (!this.canSubmit || !this.orderId) {
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { note } = this.form.getRawValue();

    this.executionApi
      .registerExecution(this.orderId, note, this.photos())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.submitted.set(true);
          void this.router.navigate(['/orders', this.orderId]);
        },
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          this.errorMessage.set(this.extractErrorMessage(error));
        },
      });
  }

  private loadOrder(orderId: string): void {
    this.loadingOrder.set(true);
    this.ordersApi
      .getOrderDetail(orderId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (order) => {
          this.order.set(order);
          this.loadingOrder.set(false);
        },
        error: () => {
          this.loadingOrder.set(false);
        },
      });
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    const body = error.error as Partial<ApiErrorResponse> | null;
    if (error.status === 422) {
      return body?.message ?? 'Debes adjuntar al menos una foto de evidencia válida.';
    }
    if (error.status === 403) {
      return body?.message ?? 'No tienes permiso para registrar la ejecución de esta orden.';
    }
    if (error.status === 409) {
      return body?.message ?? 'La orden no está en un estado que permita registrar la ejecución.';
    }
    return body?.message ?? 'No se pudo registrar la ejecución. Intenta de nuevo.';
  }
}
