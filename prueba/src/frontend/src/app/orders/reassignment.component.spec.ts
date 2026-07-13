import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ReassignmentComponent } from './reassignment.component';
import { ReassignmentApiService } from './reassignment-api.service';
import { OrderDetail } from './order.model';

describe('ReassignmentComponent', () => {
  let fixture: ComponentFixture<ReassignmentComponent>;
  let reassignmentApiSpy: jasmine.SpyObj<ReassignmentApiService>;

  const validUuid = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

  const orderAssigned: OrderDetail = {
    id: 'order-1',
    status: 'assigned',
    assignedTechnicianId: 'tech-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    executionNote: null,
    evidencePhotoIds: [],
    rejectionComment: null,
  };

  function configure(orderId: string | null = 'order-1'): void {
    reassignmentApiSpy = jasmine.createSpyObj<ReassignmentApiService>('ReassignmentApiService', ['reassignOrder']);

    TestBed.configureTestingModule({
      imports: [ReassignmentComponent],
      providers: [
        provideRouter([]),
        { provide: ReassignmentApiService, useValue: reassignmentApiSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(orderId ? { id: orderId } : {}) },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(ReassignmentComponent);
  }

  it('reasigna la orden con un UUID válido (camino feliz)', () => {
    configure();
    fixture.detectChanges();

    const reassigned: OrderDetail = { ...orderAssigned, assignedTechnicianId: validUuid };
    reassignmentApiSpy.reassignOrder.and.returnValue(of(reassigned));

    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture.componentInstance.newTechnicianIdControl.setValue(validUuid);
    expect(fixture.componentInstance.canSubmit).toBe(true);

    fixture.componentInstance.submitReassignment();

    expect(reassignmentApiSpy.reassignOrder).toHaveBeenCalledWith('order-1', validUuid);
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
    expect(fixture.componentInstance.submitted()).toBe(true);
  });

  it('bloquea el envío con un UUID de formato inválido', () => {
    configure();
    fixture.detectChanges();

    fixture.componentInstance.newTechnicianIdControl.setValue('no-es-un-uuid');

    expect(fixture.componentInstance.newTechnicianIdControl.hasError('pattern')).toBe(true);
    expect(fixture.componentInstance.canSubmit).toBe(false);

    fixture.componentInstance.submitReassignment();
    expect(reassignmentApiSpy.reassignOrder).not.toHaveBeenCalled();
  });

  it('muestra un mensaje de error 409 cuando la orden está closed', () => {
    configure();
    fixture.detectChanges();

    const errorResponse = new HttpErrorResponse({
      status: 409,
      error: { code: 'ORDER_CLOSED', message: 'La orden está cerrada y no puede reasignarse.' },
    });
    reassignmentApiSpy.reassignOrder.and.returnValue(throwError(() => errorResponse));

    fixture.componentInstance.newTechnicianIdControl.setValue(validUuid);
    fixture.componentInstance.submitReassignment();

    expect(fixture.componentInstance.errorMessage()).toBe('La orden está cerrada y no puede reasignarse.');
    expect(fixture.componentInstance.submitting()).toBe(false);
  });

  it('muestra un mensaje de error 403 cuando el usuario no es DISPATCHER', () => {
    configure();
    fixture.detectChanges();

    const errorResponse = new HttpErrorResponse({
      status: 403,
      error: { code: 'FORBIDDEN', message: 'No tienes permiso para reasignar esta orden.' },
    });
    reassignmentApiSpy.reassignOrder.and.returnValue(throwError(() => errorResponse));

    fixture.componentInstance.newTechnicianIdControl.setValue(validUuid);
    fixture.componentInstance.submitReassignment();

    expect(fixture.componentInstance.errorMessage()).toBe('No tienes permiso para reasignar esta orden.');
  });
});
