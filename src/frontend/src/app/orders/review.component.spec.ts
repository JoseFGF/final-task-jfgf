import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ReviewComponent } from './review.component';
import { OrderApiService } from './order-api.service';
import { OrderDetail } from './order.model';

describe('ReviewComponent', () => {
  let fixture: ComponentFixture<ReviewComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;
  let router: Router;

  const baseOrder: OrderDetail = {
    id: 'order-1',
    status: 'pending_review',
    assignedTechnicianId: 'tech-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    executionNote: 'Trabajo realizado según lo pactado',
    evidencePhotoIds: ['photo-1'],
    rejectionComment: null,
  };

  function configure(order: OrderDetail): void {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', [
      'getOrderDetail',
      'reviewOrder',
    ]);
    orderApiSpy.getOrderDetail.and.returnValue(of(order));

    TestBed.configureTestingModule({
      imports: [ReviewComponent],
      providers: [
        provideRouter([]),
        { provide: OrderApiService, useValue: orderApiSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: order.id }) } },
        },
      ],
    });

    fixture = TestBed.createComponent(ReviewComponent);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  }

  it('aprueba la orden y navega al detalle en el camino feliz', () => {
    configure(baseOrder);
    orderApiSpy.reviewOrder.and.returnValue(of({ ...baseOrder, status: 'closed' }));
    spyOn(router, 'navigate').and.resolveTo(true);

    fixture.componentInstance.approve();

    expect(orderApiSpy.reviewOrder).toHaveBeenCalledWith('order-1', 'APPROVE');
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
  });

  it('rechaza la orden con comentario y navega al detalle en el camino feliz', () => {
    configure(baseOrder);
    orderApiSpy.reviewOrder.and.returnValue(
      of({ ...baseOrder, status: 'in_progress', rejectionComment: 'Falta foto del panel' }),
    );
    spyOn(router, 'navigate').and.resolveTo(true);

    const component = fixture.componentInstance;
    component.toggleRejectForm();
    component.rejectForm.controls.comment.setValue('Falta foto del panel');

    expect(component.canSubmitReject).toBeTrue();
    component.reject();

    expect(orderApiSpy.reviewOrder).toHaveBeenCalledWith('order-1', 'REJECT', 'Falta foto del panel');
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
  });

  it('no permite confirmar el rechazo sin comentario (FR-012a)', () => {
    configure(baseOrder);

    const component = fixture.componentInstance;
    component.toggleRejectForm();

    expect(component.canSubmitReject).toBeFalse();

    component.reject();

    expect(orderApiSpy.reviewOrder).not.toHaveBeenCalled();
  });

  it('muestra el error del backend cuando la revisión es rechazada (409/403/422)', () => {
    configure(baseOrder);
    const errorResponse = new HttpErrorResponse({
      status: 409,
      error: { code: 'INVALID_STATE', message: 'La orden ya no está en revisión pendiente.' },
    });
    orderApiSpy.reviewOrder.and.returnValue(throwError(() => errorResponse));

    fixture.componentInstance.approve();

    expect(fixture.componentInstance.submitErrorMessage()).toBe(
      'La orden ya no está en revisión pendiente.',
    );
  });
});
