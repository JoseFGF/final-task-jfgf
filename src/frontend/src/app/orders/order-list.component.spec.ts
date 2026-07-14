import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OrderListComponent } from './order-list.component';
import { OrderApiService } from './order-api.service';
import { OrderSummary } from './order.model';
import { AuthService } from '../core/auth.service';

describe('OrderListComponent', () => {
  let fixture: ComponentFixture<OrderListComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockOrders: OrderSummary[] = [
    {
      id: 'order-1',
      status: 'in_progress',
      assignedTechnicianEmail: 'tecnico@fieldops.com',
      description: 'Revisión de panel eléctrico',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', ['listOrders']);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['hasRole']);
    authServiceSpy.hasRole.and.returnValue(false);

    await TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [
        provideRouter([]),
        { provide: OrderApiService, useValue: orderApiSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();
  });

  it('muestra las órdenes devueltas por el backend', () => {
    orderApiSpy.listOrders.and.returnValue(of(mockOrders));
    fixture = TestBed.createComponent(OrderListComponent);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.orders()).toEqual(mockOrders);
    expect(component.loading()).toBeFalse();

    const listItems = fixture.nativeElement.querySelectorAll('li');
    expect(listItems.length).toBe(1);
  });

  it('muestra un mensaje de error si la petición falla', () => {
    orderApiSpy.listOrders.and.returnValue(throwError(() => new Error('fail')));
    fixture = TestBed.createComponent(OrderListComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage()).not.toBeNull();
  });

  it('muestra el enlace "Crear orden" solo si el usuario es DISPATCHER (US3)', () => {
    orderApiSpy.listOrders.and.returnValue(of(mockOrders));
    authServiceSpy.hasRole.and.returnValue(true);
    fixture = TestBed.createComponent(OrderListComponent);
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[href="/orders/new"]');
    expect(link).toBeTruthy();
  });

  it('no muestra el enlace "Crear orden" si el usuario no es DISPATCHER', () => {
    orderApiSpy.listOrders.and.returnValue(of(mockOrders));
    authServiceSpy.hasRole.and.returnValue(false);
    fixture = TestBed.createComponent(OrderListComponent);
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a[href="/orders/new"]');
    expect(link).toBeFalsy();
  });
});
