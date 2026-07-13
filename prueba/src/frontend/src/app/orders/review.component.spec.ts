import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ReviewComponent } from './review.component';
import { ReviewApiService } from './review-api.service';
import { OrdersQueryApiService } from './orders-query-api.service';
import { OrderDetail } from './order.model';

describe('ReviewComponent', () => {
  let fixture: ComponentFixture<ReviewComponent>;
  let reviewApiSpy: jasmine.SpyObj<ReviewApiService>;
  let ordersApiSpy: jasmine.SpyObj<OrdersQueryApiService>;

  const orderPendingReview: OrderDetail = {
    id: 'order-1',
    status: 'pending_review',
    assignedTechnicianId: 'tech-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    executionNote: 'Trabajo completado',
    evidencePhotoIds: ['photo-1'],
    rejectionComment: null,
  };

  function configure(orderId: string | null = 'order-1'): void {
    reviewApiSpy = jasmine.createSpyObj<ReviewApiService>('ReviewApiService', ['reviewOrder']);
    ordersApiSpy = jasmine.createSpyObj<OrdersQueryApiService>('OrdersQueryApiService', ['getOrderDetail']);
    ordersApiSpy.getOrderDetail.and.returnValue(of(orderPendingReview));

    TestBed.configureTestingModule({
      imports: [ReviewComponent],
      providers: [
        provideRouter([]),
        { provide: ReviewApiService, useValue: reviewApiSpy },
        { provide: OrdersQueryApiService, useValue: ordersApiSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(orderId ? { id: orderId } : {}) },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(ReviewComponent);
  }

  it('aprueba la orden tras confirmar (camino feliz)', () => {
    configure();
    fixture.detectChanges();

    const closed: OrderDetail = { ...orderPendingReview, status: 'closed' };
    reviewApiSpy.reviewOrder.and.returnValue(of(closed));

    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture.componentInstance.requestApprove();
    expect(fixture.componentInstance.confirmingApprove()).toBe(true);

    fixture.componentInstance.confirmApprove();

    expect(reviewApiSpy.reviewOrder).toHaveBeenCalledWith('order-1', 'APPROVE', undefined);
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
  });

  it('rechaza la orden con un comentario no vacío (camino feliz)', () => {
    configure();
    fixture.detectChanges();

    const rejected: OrderDetail = { ...orderPendingReview, status: 'in_progress', rejectionComment: 'Falta foto clara.' };
    reviewApiSpy.reviewOrder.and.returnValue(of(rejected));

    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture.componentInstance.openRejectForm();
    fixture.componentInstance.rejectForm.controls.comment.setValue('Falta foto clara.');

    expect(fixture.componentInstance.canReject).toBe(true);

    fixture.componentInstance.submitReject();

    expect(reviewApiSpy.reviewOrder).toHaveBeenCalledWith('order-1', 'REJECT', 'Falta foto clara.');
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
  });

  it('bloquea el rechazo sin comentario', () => {
    configure();
    fixture.detectChanges();

    fixture.componentInstance.openRejectForm();

    expect(fixture.componentInstance.rejectForm.controls.comment.value).toBe('');
    expect(fixture.componentInstance.canReject).toBe(false);

    fixture.componentInstance.submitReject();
    expect(reviewApiSpy.reviewOrder).not.toHaveBeenCalled();

    fixture.componentInstance.rejectForm.controls.comment.setValue('   ');
    expect(fixture.componentInstance.canReject).toBe(false);
  });

  it('muestra un mensaje de error 422 cuando el backend rechaza por comentario vacío', () => {
    configure();
    fixture.detectChanges();

    const errorResponse = new HttpErrorResponse({
      status: 422,
      error: { code: 'MISSING_COMMENT', message: 'El comentario de rechazo no puede estar vacío.' },
    });
    reviewApiSpy.reviewOrder.and.returnValue(throwError(() => errorResponse));

    fixture.componentInstance.openRejectForm();
    fixture.componentInstance.rejectForm.controls.comment.setValue('Motivo');
    fixture.componentInstance.submitReject();

    expect(fixture.componentInstance.errorMessage()).toBe('El comentario de rechazo no puede estar vacío.');
  });

  it('muestra un mensaje de error 403 cuando el usuario no es SUPERVISOR', () => {
    configure();
    fixture.detectChanges();

    const errorResponse = new HttpErrorResponse({
      status: 403,
      error: { code: 'FORBIDDEN', message: 'No tienes permiso para revisar esta orden.' },
    });
    reviewApiSpy.reviewOrder.and.returnValue(throwError(() => errorResponse));

    fixture.componentInstance.requestApprove();
    fixture.componentInstance.confirmApprove();

    expect(fixture.componentInstance.errorMessage()).toBe('No tienes permiso para revisar esta orden.');
  });

  it('muestra un mensaje de error 409 cuando la orden no está en pending_review', () => {
    configure();
    fixture.detectChanges();

    const errorResponse = new HttpErrorResponse({
      status: 409,
      error: { code: 'INVALID_STATE', message: 'La orden no está en pending_review.' },
    });
    reviewApiSpy.reviewOrder.and.returnValue(throwError(() => errorResponse));

    fixture.componentInstance.requestApprove();
    fixture.componentInstance.confirmApprove();

    expect(fixture.componentInstance.errorMessage()).toBe('La orden no está en pending_review.');
  });
});
