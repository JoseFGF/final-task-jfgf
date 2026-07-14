import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OrderApiService } from './order-api.service';
import { ApiErrorResponse } from './order.model';

interface OrderCreateFormValue {
  description: FormControl<string>;
  technicianEmail: FormControl<string>;
}

/**
 * Creación de una orden nueva por un dispatcher (US3, FR-007 a FR-007c,
 * FR-008). `technicianEmail` es opcional: si se aporta, la orden se crea
 * directamente `assigned`; si no, queda en `draft` sin technician.
 */
@Component({
  selector: 'app-order-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './order-create.component.html',
})
export class OrderCreateComponent {
  readonly form = new FormGroup<OrderCreateFormValue>({
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    technicianEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.email],
    }),
  });

  readonly submitting = signal<boolean>(false);
  readonly submitErrorMessage = signal<string | null>(null);

  private readonly router = inject(Router);
  private readonly orderApi = inject(OrderApiService);

  get canSubmit(): boolean {
    return this.form.valid && !this.submitting();
  }

  create(): void {
    if (!this.canSubmit) {
      return;
    }

    this.submitting.set(true);
    this.submitErrorMessage.set(null);

    const description = this.form.controls.description.value.trim();
    const technicianEmail = this.form.controls.technicianEmail.value.trim();

    this.orderApi.createOrder(description, technicianEmail || undefined).subscribe({
      next: (order) => {
        this.submitting.set(false);
        void this.router.navigate(['/orders', order.id]);
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
      return body?.message ?? 'No tienes permiso de dispatcher para crear órdenes.';
    }
    if (error.status === 422) {
      return (
        body?.message ??
        'No se pudo crear la orden: revisa la descripción y el email del technician.'
      );
    }
    return body?.message ?? 'No se pudo crear la orden. Intenta de nuevo.';
  }
}
