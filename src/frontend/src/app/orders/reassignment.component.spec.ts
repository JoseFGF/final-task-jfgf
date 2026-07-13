import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ReassignmentComponent } from './reassignment.component';
import { OrderApiService } from './order-api.service';
import { OrderDetail } from './order.model';

describe('ReassignmentComponent', () => {
  let fixture: ComponentFixture<ReassignmentComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;
  let router: Router;

  const baseOrder: OrderDetail = {
    id: 'order-1',
    status: 'in_progress',
    assignedTechnicianId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    executionNote: null,
    evidencePhotoIds: [],
    rejectionComment: null,
  };

  const newTechnicianId = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

  function configure(order: OrderDetail): void {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', [
      'getOrderDetail',
      'reassignOrder',
    ]);
    orderApiSpy.getOrderDetail.and.returnValue(of(order));

    TestBed.configureTestingModule({
      imports: [ReassignmentComponent],
      providers: [
        provideRouter([]),
        { provide: OrderApiService, useValue: orderApiSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: order.id }) } },
        },
      ],
    });

    fixture = TestBed.createComponent(ReassignmentComponent);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  }

  it('reasigna la orden y navega al detalle en el camino feliz', () => {
    configure(baseOrder);
    orderApiSpy.reassignOrder.and.returnValue(
      of({ ...baseOrder, assignedTechnicianId: newTechnicianId }),
    );
    spyOn(router, 'navigate').and.resolveTo(true);

    const component = fixture.componentInstance;
    component.form.controls.newTechnicianId.setValue(newTechnicianId);

    expect(component.canSubmit).toBeTrue();

    component.reassign();

    expect(orderApiSpy.reassignOrder).toHaveBeenCalledWith('order-1', newTechnicianId);
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
  });

  it('no envía la reasignación si el UUID introducido no tiene formato válido', () => {
    configure(baseOrder);

    const component = fixture.componentInstance;
    component.form.controls.newTechnicianId.setValue('no-es-un-uuid');

    expect(component.canSubmit).toBeFalse();

    component.reassign();

    expect(orderApiSpy.reassignOrder).not.toHaveBeenCalled();
  });

  it('muestra el error del backend cuando la orden ya está closed (409, FR-014)', () => {
    configure(baseOrder);
    const errorResponse = new HttpErrorResponse({
      status: 409,
      error: { code: 'INVALID_STATE', message: 'La orden está cerrada y ya no admite reasignación.' },
    });
    orderApiSpy.reassignOrder.and.returnValue(throwError(() => errorResponse));

    const component = fixture.componentInstance;
    component.form.controls.newTechnicianId.setValue(newTechnicianId);
    component.reassign();

    expect(component.submitErrorMessage()).toBe(
      'La orden está cerrada y ya no admite reasignación.',
    );
  });

  it('muestra el error del backend cuando el usuario no es dispatcher (403, FR-014)', () => {
    configure(baseOrder);
    const errorResponse = new HttpErrorResponse({
      status: 403,
      error: { code: 'FORBIDDEN', message: 'No tienes permiso de dispatcher para reasignar esta orden.' },
    });
    orderApiSpy.reassignOrder.and.returnValue(throwError(() => errorResponse));

    const component = fixture.componentInstance;
    component.form.controls.newTechnicianId.setValue(newTechnicianId);
    component.reassign();

    expect(component.submitErrorMessage()).toBe(
      'No tienes permiso de dispatcher para reasignar esta orden.',
    );
  });
});
