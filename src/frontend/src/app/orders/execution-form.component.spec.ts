import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ExecutionFormComponent } from './execution-form.component';
import { OrderApiService } from './order-api.service';
import { OrderDetail } from './order.model';

describe('ExecutionFormComponent', () => {
  let fixture: ComponentFixture<ExecutionFormComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;
  let router: Router;

  const baseOrder: OrderDetail = {
    id: 'order-1',
    status: 'in_progress',
    assignedTechnicianId: 'tech-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    executionNote: null,
    evidencePhotoIds: [],
    rejectionComment: null,
  };

  function configure(order: OrderDetail): void {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', [
      'getOrderDetail',
      'registerExecution',
    ]);
    orderApiSpy.getOrderDetail.and.returnValue(of(order));

    TestBed.configureTestingModule({
      imports: [ExecutionFormComponent],
      providers: [
        provideRouter([]),
        { provide: OrderApiService, useValue: orderApiSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: order.id }) } },
        },
      ],
    });

    fixture = TestBed.createComponent(ExecutionFormComponent);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  }

  it('no permite enviar sin al menos una foto adjunta (FR-006)', () => {
    configure(baseOrder);
    const component = fixture.componentInstance;
    component.form.controls.note.setValue('Trabajo realizado');

    expect(component.hasPhotos).toBeFalse();
    expect(component.canSubmit).toBeFalse();
  });

  it('registra la ejecución y navega al detalle en el camino feliz', () => {
    configure(baseOrder);
    orderApiSpy.registerExecution.and.returnValue(of(baseOrder));
    spyOn(router, 'navigate').and.resolveTo(true);

    const component = fixture.componentInstance;
    component.form.controls.note.setValue('Trabajo realizado');
    component.photos.set([new File(['x'], 'foto.jpg', { type: 'image/jpeg' })]);

    expect(component.canSubmit).toBeTrue();
    component.submit();

    expect(orderApiSpy.registerExecution).toHaveBeenCalledWith(
      'order-1',
      'Trabajo realizado',
      component.photos(),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
  });

  it('muestra el rejectionComment previo en un aviso destacado (FR-022)', () => {
    configure({ ...baseOrder, rejectionComment: 'Foto ilegible, repetir' });

    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('Foto ilegible, repetir');
  });

  it('muestra el error 422 del backend cuando la foto es inválida', () => {
    configure(baseOrder);
    const errorResponse = new HttpErrorResponse({
      status: 422,
      error: { code: 'INVALID_PHOTO', message: 'La foto adjunta no es una imagen válida.' },
    });
    orderApiSpy.registerExecution.and.returnValue(throwError(() => errorResponse));

    const component = fixture.componentInstance;
    component.form.controls.note.setValue('Trabajo realizado');
    component.photos.set([new File(['x'], 'foto.jpg', { type: 'image/jpeg' })]);
    component.submit();

    expect(component.submitErrorMessage()).toBe('La foto adjunta no es una imagen válida.');
  });
});
