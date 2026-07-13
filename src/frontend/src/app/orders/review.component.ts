import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { OrderApiService } from './order-api.service';
import { ApiErrorResponse, OrderDetail } from './order.model';
import { IncidentSummaryComponent } from './incident-summary.component';

interface ReviewFormValue {
  comment: FormControl<string>;
}

/**
 * Revisión de una orden en `pending_review` por parte de un supervisor (US3,
 * FR-009 a FR-012a). Aprobar no requiere campos adicionales; rechazar exige
 * un comentario no vacío antes de habilitar el envío (validación de UX — el
 * backend es la autoridad real, FR-012a).
 */
@Component({
  selector: 'app-review',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, IncidentSummaryComponent],
  templateUrl: './review.component.html',
})
export class ReviewComponent implements OnInit {
  readonly rejectForm = new FormGroup<ReviewFormValue>({
    comment: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly order = signal<OrderDetail | null>(null);
  readonly loading = signal<boolean>(true);
  readonly submitting = signal<boolean>(false);
  readonly loadErrorMessage = signal<string | null>(null);
  readonly submitErrorMessage = signal<string | null>(null);
  readonly showRejectForm = signal<boolean>(false);

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

  get canSubmitReject(): boolean {
    return this.rejectForm.valid && this.rejectForm.controls.comment.value.trim().length > 0 && !this.submitting();
  }

  toggleRejectForm(): void {
    this.showRejectForm.set(true);
    this.submitErrorMessage.set(null);
  }

  approve(): void {
    if (this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.submitErrorMessage.set(null);

    this.orderApi.reviewOrder(this.orderId, 'APPROVE').subscribe({
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

  reject(): void {
    if (!this.canSubmitReject) {
      return;
    }

    this.submitting.set(true);
    this.submitErrorMessage.set(null);

    const comment = this.rejectForm.controls.comment.value.trim();

    this.orderApi.reviewOrder(this.orderId, 'REJECT', comment).subscribe({
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
      return body?.message ?? 'No se pudo rechazar la orden: se requiere un comentario no vacío.';
    }
    if (error.status === 403) {
      return body?.message ?? 'No tienes permiso de supervisor para revisar esta orden.';
    }
    if (error.status === 409) {
      return body?.message ?? 'La orden ya no está en revisión pendiente.';
    }
    return body?.message ?? 'No se pudo completar la revisión. Intenta de nuevo.';
  }
}
