import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { OrderCreateComponent } from './order-create.component';
import { OrderApiService } from './order-api.service';
import { OrderDetail } from './order.model';

describe('OrderCreateComponent', () => {
  let fixture: ComponentFixture<OrderCreateComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;
  let router: Router;

  const createdOrder: OrderDetail = {
    id: 'order-1',
    status: 'draft',
    assignedTechnicianEmail: null,
    description: 'Instalación de equipo',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    executionNote: null,
    evidencePhotoIds: [],
    rejectionComment: null,
  };

  beforeEach(() => {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', ['createOrder']);

    TestBed.configureTestingModule({
      imports: [OrderCreateComponent],
      providers: [provideRouter([]), { provide: OrderApiService, useValue: orderApiSpy }],
    });

    fixture = TestBed.createComponent(OrderCreateComponent);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('no permite enviar sin descripción', () => {
    const component = fixture.componentInstance;
    expect(component.canSubmit).toBeFalse();
  });

  it('no permite enviar una descripción con solo espacios en blanco', () => {
    const component = fixture.componentInstance;
    component.form.controls.description.setValue('   ');

    expect(component.canSubmit).toBeFalse();
    component.create();

    expect(orderApiSpy.createOrder).not.toHaveBeenCalled();
  });

  it('crea la orden solo con descripción y navega al detalle', () => {
    orderApiSpy.createOrder.and.returnValue(of(createdOrder));
    spyOn(router, 'navigate').and.resolveTo(true);

    const component = fixture.componentInstance;
    component.form.controls.description.setValue('Instalación de equipo');

    expect(component.canSubmit).toBeTrue();
    component.create();

    expect(orderApiSpy.createOrder).toHaveBeenCalledWith('Instalación de equipo', undefined);
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
  });

  it('crea la orden con descripción y email de technician', () => {
    orderApiSpy.createOrder.and.returnValue(
      of({ ...createdOrder, status: 'assigned', assignedTechnicianEmail: 'tecnico@fieldops.com' }),
    );
    spyOn(router, 'navigate').and.resolveTo(true);

    const component = fixture.componentInstance;
    component.form.controls.description.setValue('Instalación de equipo');
    component.form.controls.technicianEmail.setValue('tecnico@fieldops.com');
    component.create();

    expect(orderApiSpy.createOrder).toHaveBeenCalledWith(
      'Instalación de equipo',
      'tecnico@fieldops.com',
    );
  });

  it('no envía si el email de technician no tiene formato válido', () => {
    const component = fixture.componentInstance;
    component.form.controls.description.setValue('Instalación de equipo');
    component.form.controls.technicianEmail.setValue('no-es-un-email');

    expect(component.canSubmit).toBeFalse();
    component.create();

    expect(orderApiSpy.createOrder).not.toHaveBeenCalled();
  });

  it('muestra el error del backend cuando el usuario no es dispatcher (403, FR-008)', () => {
    const errorResponse = new HttpErrorResponse({
      status: 403,
      error: { code: 'FORBIDDEN', message: 'No tienes permiso de dispatcher para crear órdenes.' },
    });
    orderApiSpy.createOrder.and.returnValue(throwError(() => errorResponse));

    const component = fixture.componentInstance;
    component.form.controls.description.setValue('Instalación de equipo');
    component.create();

    expect(component.submitErrorMessage()).toBe(
      'No tienes permiso de dispatcher para crear órdenes.',
    );
  });
});
