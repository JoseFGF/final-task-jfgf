import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReassignmentApiService } from './reassignment-api.service';
import { ApiErrorResponse } from '../core/auth.model';

/** Regex estándar de validación de formato UUID (v1-v5, sin distinguir versión/variant). */
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

interface ReassignFormValue {
  newTechnicianId: FormControl<string>;
}

/**
 * US4 — Reasignar una orden entre técnicos
 * (`POST /api/v1/orders/{orderId}/reassignment`, FR-013, FR-014, FR-021).
 * Solo el DISPATCHER puede reasignar, y solo si la orden no está `closed`
 * (autorización y validación de estado reales las aplica el backend).
 *
 * LIMITACIÓN CONOCIDA: este slice recortado del contrato no expone ningún
 * endpoint para listar technicians (p.ej. `GET /technicians`), por lo que el
 * formulario pide el UUID del nuevo technician como texto libre en lugar de
 * un selector. Un pase de integración posterior podría sustituir este input
 * por un `<select>` alimentado por un endpoint de directorio de personal,
 * si el backend llega a incorporarlo.
 */
@Component({
  selector: 'app-reassignment',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './reassignment.component.html',
})
export class ReassignmentComponent implements OnInit {
  readonly reassignForm = new FormGroup<ReassignFormValue>({
    newTechnicianId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(UUID_PATTERN)],
    }),
  });

  readonly submitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly submitted = signal<boolean>(false);

  private readonly reassignmentApi = inject(ReassignmentApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private orderId: string | null = null;

  get newTechnicianIdControl(): FormControl<string> {
    return this.reassignForm.controls.newTechnicianId;
  }

  get canSubmit(): boolean {
    return this.reassignForm.valid && !this.submitting();
  }

  ngOnInit(): void {
    this.orderId = this.route.snapshot.paramMap.get('id');
    if (!this.orderId) {
      this.errorMessage.set('Falta el identificador de la orden.');
    }
  }

  submitReassignment(): void {
    if (!this.orderId || !this.canSubmit) {
      return;
    }

    const newTechnicianId = this.newTechnicianIdControl.value.trim();
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.reassignmentApi
      .reassignOrder(this.orderId, newTechnicianId)
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

  private extractErrorMessage(error: HttpErrorResponse): string {
    const body = error.error as Partial<ApiErrorResponse> | null;
    if (error.status === 403) {
      return body?.message ?? 'No tienes permiso para reasignar esta orden.';
    }
    if (error.status === 404) {
      return body?.message ?? 'La orden no existe.';
    }
    if (error.status === 409) {
      return body?.message ?? 'La orden está cerrada y no puede reasignarse.';
    }
    return body?.message ?? 'No se pudo reasignar la orden. Intenta de nuevo.';
  }
}
