import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { OrderApiService } from './order-api.service';
import { ApiErrorResponse, OrderDetail, OrderStatus } from './order.model';
import { AuthService } from '../core/auth.service';

/**
 * Tabla de adyacencia de transiciones de estado reconocidas
 * (`data-model.md`, ADR-010), replicada aquí solo para decisiones de UX: qué
 * destinos ofrecer en la corrección manual de dispatcher/supervisor. El
 * backend (`OrderStatusService`) es la única fuente de verdad real.
 */
const MANUAL_STATUS_ADJACENCY: Readonly<Record<OrderStatus, readonly OrderStatus[]>> = {
  // `draft -> assigned` es una transición adyacente válida (`data-model.md`),
  // pero normalmente requiere indicar un technician, algo que este endpoint
  // no gestiona (`OrderStatusChangeRequest` solo lleva `status`). Se omite
  // deliberadamente el botón aquí para no ofrecer una acción que confundiría
  // al dispatcher/supervisor (ver nota en el resumen de la tarea T022a).
  draft: [],
  assigned: ['draft', 'in_progress'],
  in_progress: ['assigned', 'pending_review'],
  pending_review: ['in_progress', 'closed'],
  closed: [],
};

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
export class OrderDetailComponent implements OnInit, OnDestroy {
  readonly order = signal<OrderDetail | null>(null);
  readonly loading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);

  /** Object URLs (US4) generados a partir de los blobs de evidencia fotográfica. */
  readonly photoUrls = signal<string[]>([]);

  /** Mensaje de error de la última acción de cambio de estado (iniciar trabajo o corrección manual). */
  readonly statusChangeError = signal<string | null>(null);

  /** `true` mientras una petición de cambio de estado está en curso (evita envíos duplicados). */
  readonly statusChangeSubmitting = signal<boolean>(false);

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

  /**
   * `true` si al usuario actual (rol TECHNICIAN) le corresponde poder
   * iniciar el trabajo de esta orden, es decir, está `assigned` (FR-001 a
   * FR-003). Es un control de UX (Principio II): el frontend no puede saber
   * con certeza si es el technician *asignado* (el JWT no expone su email,
   * solo un UUID interno), así que se muestra de forma optimista; el
   * backend es quien valida realmente la propiedad al recibir
   * `POST /orders/{orderId}/status` (403 en caso contrario), igual que
   * `canReview`/`canReassign` ya hacen con sus propias reglas.
   */
  readonly canStartWork = computed<boolean>(() => {
    const currentOrder = this.order();
    return (
      currentOrder !== null &&
      currentOrder.status === 'assigned' &&
      this.authService.hasRole('TECHNICIAN')
    );
  });

  /**
   * Destinos adyacentes válidos al estado actual de la orden, según la tabla
   * de adyacencia (`data-model.md`, ADR-010). Vacío si la orden es `closed`
   * o si no hay orden cargada.
   */
  readonly manualStatusOptions = computed<OrderStatus[]>(() => {
    const currentOrder = this.order();
    if (currentOrder === null) {
      return [];
    }
    return [...MANUAL_STATUS_ADJACENCY[currentOrder.status]];
  });

  /**
   * `true` si al usuario actual (rol DISPATCHER/SUPERVISOR) le corresponde
   * poder corregir manualmente el estado de esta orden, es decir, existe al
   * menos un destino adyacente disponible (FR-004 a FR-006). Es un control
   * de UX (Principio II): el backend es quien valida realmente el rol y la
   * adyacencia al recibir `POST /orders/{orderId}/status`.
   */
  readonly canChangeStatusManually = computed<boolean>(
    () => this.authService.hasRole('DISPATCHER', 'SUPERVISOR') && this.manualStatusOptions().length > 0,
  );

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
        this.loadEvidencePhotos(order);
      },
      error: () => {
        this.errorMessage.set('No se pudo cargar el detalle de la orden.');
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.revokePhotoUrls();
  }

  /**
   * "Iniciar trabajo" (FR-001 a FR-003, US1): el technician asignado marca
   * la orden `assigned` como `in_progress`.
   */
  startWork(): void {
    this.changeStatus('in_progress');
  }

  /**
   * Corrección manual de estado (FR-004 a FR-006, US2): dispatcher/supervisor
   * mueven la orden a un destino adyacente al estado actual.
   */
  changeStatusManually(target: OrderStatus): void {
    this.changeStatus(target);
  }

  private changeStatus(target: OrderStatus): void {
    this.statusChangeError.set(null);
    this.statusChangeSubmitting.set(true);

    this.orderApi.changeOrderStatus(this.orderId, target).subscribe({
      next: () => {
        this.statusChangeSubmitting.set(false);
        this.loadOrder();
      },
      error: (error: HttpErrorResponse) => {
        this.statusChangeSubmitting.set(false);
        this.statusChangeError.set(this.extractStatusChangeErrorMessage(error));
      },
    });
  }

  private extractStatusChangeErrorMessage(error: HttpErrorResponse): string {
    const body = error.error as Partial<ApiErrorResponse> | null;
    if (error.status === 403) {
      return body?.message ?? 'No tienes permiso para cambiar el estado de esta orden.';
    }
    if (error.status === 409) {
      return body?.message ?? 'La orden no admite ese cambio de estado desde su estado actual.';
    }
    if (error.status === 422) {
      return body?.message ?? 'Esta orden no tiene fotos de evidencia registradas todavía.';
    }
    if (error.status === 404) {
      return body?.message ?? 'La orden no existe.';
    }
    return body?.message ?? 'No se pudo cambiar el estado de la orden. Intenta de nuevo.';
  }

  /**
   * Descarga cada foto de evidencia como blob autenticado (US4) y la expone
   * como Object URL para poder usarla en un `<img [src]>`. Un `<img src="...">`
   * directo al endpoint no enviaría el header `Authorization`.
   */
  private loadEvidencePhotos(order: OrderDetail): void {
    this.revokePhotoUrls();

    if (order.evidencePhotoIds.length === 0) {
      this.photoUrls.set([]);
      return;
    }

    forkJoin(
      order.evidencePhotoIds.map((photoId) =>
        this.orderApi.getEvidencePhoto(order.id, photoId).pipe(catchError(() => of(null))),
      ),
    ).subscribe((blobs) => {
      const urls = blobs
        .filter((blob): blob is Blob => blob !== null)
        .map((blob) => URL.createObjectURL(blob));
      this.photoUrls.set(urls);
    });
  }

  private revokePhotoUrls(): void {
    this.photoUrls().forEach((url) => URL.revokeObjectURL(url));
  }
}
