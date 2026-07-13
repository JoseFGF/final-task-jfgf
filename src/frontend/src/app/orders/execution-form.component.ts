import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OrderApiService } from './order-api.service';
import { ApiErrorResponse, OrderDetail } from './order.model';

interface ExecutionFormValue {
  note: FormControl<string>;
}

/**
 * Formulario de registro de ejecución (US2, FR-005 a FR-008). Exige al menos
 * una foto antes de habilitar el envío (validación de UX; el backend es la
 * autoridad real, FR-006/FR-006a) y destaca el `rejectionComment` previo si
 * la orden vuelve de un rechazo (FR-022).
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
  readonly photos = signal<File[]>([]);
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

  /** `true` si hay al menos una foto adjunta (FR-006, validación de UX). */
  get hasPhotos(): boolean {
    return this.photos().length > 0;
  }

  get canSubmit(): boolean {
    return this.form.valid && this.hasPhotos && !this.submitting();
  }

  onPhotosSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    this.photos.set(files);
  }

  submit(): void {
    if (!this.canSubmit) {
      return;
    }

    this.submitting.set(true);
    this.submitErrorMessage.set(null);

    const note = this.form.controls.note.value;

    this.orderApi.registerExecution(this.orderId, note, this.photos()).subscribe({
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
    if (error.status === 422) {
      return (
        body?.message ??
        'No se pudo registrar la ejecución: se requiere al menos una foto válida (JPEG/PNG).'
      );
    }
    return body?.message ?? 'No se pudo registrar la ejecución. Intenta de nuevo.';
  }
}
