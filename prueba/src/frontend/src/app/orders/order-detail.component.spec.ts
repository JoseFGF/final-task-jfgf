import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { OrderDetailComponent } from './order-detail.component';
import { OrdersQueryApiService } from './orders-query-api.service';
import { OrderDetail } from './order.model';
import { AuthService } from '../core/auth.service';

describe('OrderDetailComponent', () => {
  let fixture: ComponentFixture<OrderDetailComponent>;
  let ordersApiSpy: jasmine.SpyObj<OrdersQueryApiService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const detail: OrderDetail = {
    id: 'order-1',
    status: 'in_progress',
    assignedTechnicianId: 'tech-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    executionNote: 'Se reemplazó la pieza defectuosa.',
    evidencePhotoIds: ['photo-1'],
    rejectionComment: 'Falta evidencia clara de la reparación.',
  };

  function configure(orderId: string | null = 'order-1', hasRoleReturnValue = false): void {
    ordersApiSpy = jasmine.createSpyObj<OrdersQueryApiService>('OrdersQueryApiService', ['getOrderDetail']);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['hasRole']);
    authServiceSpy.hasRole.and.returnValue(hasRoleReturnValue);

    TestBed.configureTestingModule({
      imports: [OrderDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        { provide: OrdersQueryApiService, useValue: ordersApiSpy },
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(orderId ? { id: orderId } : {}) },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(OrderDetailComponent);
  }

  it('carga el detalle de la orden y destaca el rejectionComment', () => {
    configure();
    ordersApiSpy.getOrderDetail.and.returnValue(of(detail));

    fixture.detectChanges();

    expect(ordersApiSpy.getOrderDetail).toHaveBeenCalledWith('order-1');
    expect(fixture.componentInstance.order()).toEqual(detail);

    const banner = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(banner?.textContent).toContain('Falta evidencia clara de la reparación.');
  });

  it('no muestra el banner de rechazo cuando rejectionComment es null', () => {
    configure();
    ordersApiSpy.getOrderDetail.and.returnValue(of({ ...detail, rejectionComment: null }));

    fixture.detectChanges();

    const banners = (fixture.nativeElement as HTMLElement).querySelectorAll('[role="alert"]');
    expect(banners.length).toBe(0);
  });

  it('muestra un mensaje de error 403 cuando el backend rechaza el acceso', () => {
    configure();
    const errorResponse = new HttpErrorResponse({
      status: 403,
      error: { code: 'FORBIDDEN', message: 'No tienes permiso para ver esta orden.' },
    });
    ordersApiSpy.getOrderDetail.and.returnValue(throwError(() => errorResponse));

    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage()).toBe('No tienes permiso para ver esta orden.');
  });

  it('muestra un error si falta el identificador de la orden en la ruta', () => {
    configure(null);

    fixture.detectChanges();

    expect(ordersApiSpy.getOrderDetail).not.toHaveBeenCalled();
    expect(fixture.componentInstance.errorMessage()).toBe('Falta el identificador de la orden.');
  });

  it('muestra el enlace de registrar ejecución cuando el usuario es TECHNICIAN y la orden está in_progress', () => {
    configure('order-1', true);
    ordersApiSpy.getOrderDetail.and.returnValue(of({ ...detail, status: 'in_progress' }));

    fixture.detectChanges();

    expect(fixture.componentInstance.canRegisterExecution).toBe(true);
    const link = (fixture.nativeElement as HTMLElement).querySelector('a[href="/orders/order-1/execution"]');
    expect(link).toBeTruthy();
  });

  it('no muestra ningún enlace de acción cuando el rol no tiene permiso', () => {
    configure('order-1', false);
    ordersApiSpy.getOrderDetail.and.returnValue(of({ ...detail, status: 'in_progress' }));

    fixture.detectChanges();

    expect(fixture.componentInstance.canRegisterExecution).toBe(false);
    expect(fixture.componentInstance.canReview).toBe(false);
    expect(fixture.componentInstance.canReassign).toBe(false);
    const nav = (fixture.nativeElement as HTMLElement).querySelector('nav[aria-label="Acciones sobre la orden"]');
    expect(nav).toBeFalsy();
  });

  it('no muestra el enlace de reasignar cuando la orden está closed', () => {
    configure('order-1', true);
    ordersApiSpy.getOrderDetail.and.returnValue(of({ ...detail, status: 'closed', rejectionComment: null }));

    fixture.detectChanges();

    expect(fixture.componentInstance.canReassign).toBe(false);
  });
});
