import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReviewApiService } from './review-api.service';
import { OrdersQueryApiService } from './orders-query-api.service';
import { OrderDetail } from './order.model';
import { ApiErrorResponse } from '../core/auth.model';

interface RejectFormValue {
  comment: FormControl<string>;
}

/**
 * US3 — Aprobar o rechazar una orden en revisión
 * (`POST /api/v1/orders/{orderId}/review`, FR-009 a FR-012a). Solo el
 * SUPERVISOR y con la orden en `pending_review` puede completar esta acción
 * (autorización real la aplica el backend); aprobar cierra la orden, y
 * rechazar exige un comentario no vacío (FR-012a) que vuelve la orden a
 * `in_progress` para que el técnico la corrija (FR-012, FR-022).
 */
@Component({
  selector: 'app-review',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './review.component.html',
})
export class ReviewComponent implements OnInit {
  readonly rejectForm = new FormGroup<RejectFormValue>({
    comment: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly order = signal<OrderDetail | null>(null);
  readonly loadingOrder = signal<boolean>(true);
  readonly submitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showRejectForm = signal<boolean>(false);
  readonly confirmingApprove = signal<boolean>(false);
  readonly submitted = signal<boolean>(false);

  private readonly reviewApi = inject(ReviewApiService);
  private readonly ordersApi = inject(OrdersQueryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private orderId: string | null = null;

  get canReject(): boolean {
    return this.rejectForm.valid && this.rejectForm.controls.comment.value.trim().length > 0 && !this.submitting();
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

  requestApprove(): void {
    this.confirmingApprove.set(true);
  }

  cancelApprove(): void {
    this.confirmingApprove.set(false);
  }

  confirmApprove(): void {
    this.confirmingApprove.set(false);
    this.submitReview('APPROVE');
  }

  openRejectForm(): void {
    this.showRejectForm.set(true);
  }

  cancelReject(): void {
    this.showRejectForm.set(false);
    this.rejectForm.reset({ comment: '' });
  }

  submitReject(): void {
    if (!this.canReject) {
      return;
    }
    const comment = this.rejectForm.controls.comment.value.trim();
    this.submitReview('REJECT', comment);
  }

  private submitReview(decision: 'APPROVE' | 'REJECT', comment?: string): void {
    if (!this.orderId) {
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.reviewApi
      .reviewOrder(this.orderId, decision, comment)
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
      return body?.message ?? 'Debes indicar un comentario no vacío para rechazar la orden.';
    }
    if (error.status === 403) {
      return body?.message ?? 'No tienes permiso para revisar esta orden.';
    }
    if (error.status === 409) {
      return body?.message ?? 'La orden no está en un estado que permita revisarla.';
    }
    return body?.message ?? 'No se pudo procesar la revisión. Intenta de nuevo.';
  }
}
