import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OrderApiService } from './order-api.service';
import { ApiErrorResponse, OrderDetail } from './order.model';

interface ReassignmentFormValue {
  newTechnicianEmail: FormControl<string>;
}

/**
 * Reasignación de una orden a otro technician por parte de un dispatcher
 * (US1/US4, FR-013, FR-014, FR-021).
 *
 * Limitación conocida de este slice: no existe un endpoint para listar
 * technicians (`contracts/openapi.yaml`), así que se pide el email del nuevo
 * technician mediante un input de texto en vez de un select poblado desde el
 * backend. La validación de formato aquí es solo UX; el backend es quien
 * valida realmente que el email exista y sea un TECHNICIAN válido.
 */
@Component({
  selector: 'app-reassignment',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './reassignment.component.html',
})
export class ReassignmentComponent implements OnInit {
  readonly form = new FormGroup<ReassignmentFormValue>({
    newTechnicianEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });

  readonly order = signal<OrderDetail | null>(null);
  readonly loading = signal<boolean>(true);
  readonly submitting = signal<boolean>(false);
  readonly loadErrorMessage = signal<string | null>(null);
  readonly submitErrorMessage = signal<string | null>(null);

  private orderId = '';

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orderApi = inject(OrderApiService);

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('id') ?? '';

    if (!this.orderId) {
      this.loadErrorMessage.set('Orden no encontrada.');
      this.loading.set(false);
      return;
    }

    this.orderApi.getOrderDetail(this.orderId).subscribe({
      next: (order) => {
        this.order.set(order);
        this.loading.set(false);
      },
      error: () => {
        this.loadErrorMessage.set('No se pudo cargar la orden.');
        this.loading.set(false);
      },
    });
  }

  get canSubmit(): boolean {
    return this.form.valid && !this.submitting();
  }

  reassign(): void {
    if (!this.canSubmit) {
      return;
    }

    this.submitting.set(true);
    this.submitErrorMessage.set(null);

    const newTechnicianEmail = this.form.controls.newTechnicianEmail.value.trim();

    this.orderApi.reassignOrder(this.orderId, newTechnicianEmail).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigate(['/orders', this.orderId]);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.submitErrorMessage.set(this.extractErrorMessage(error));
      },
    });
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    const body = error.error as Partial<ApiErrorResponse> | null;
    if (error.status === 403) {
      return body?.message ?? 'No tienes permiso de dispatcher para reasignar esta orden.';
    }
    if (error.status === 409) {
      return body?.message ?? 'La orden está cerrada y ya no admite reasignación.';
    }
    if (error.status === 404) {
      return body?.message ?? 'La orden no existe.';
    }
    return body?.message ?? 'No se pudo reasignar la orden. Intenta de nuevo.';
  }
}
