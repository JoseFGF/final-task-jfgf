import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { OrderListComponent } from './order-list.component';
import { OrdersQueryApiService } from './orders-query-api.service';
import { OrderSummary } from './order.model';

describe('OrderListComponent', () => {
  let fixture: ComponentFixture<OrderListComponent>;
  let ordersApiSpy: jasmine.SpyObj<OrdersQueryApiService>;

  const summaries: OrderSummary[] = [
    {
      id: 'order-1',
      status: 'assigned',
      assignedTechnicianId: 'tech-1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 'order-2',
      status: 'closed',
      assignedTechnicianId: 'tech-2',
      createdAt: '2026-01-02T00:00:00Z',
      updatedAt: '2026-01-03T00:00:00Z',
    },
  ];

  function configure(): void {
    ordersApiSpy = jasmine.createSpyObj<OrdersQueryApiService>('OrdersQueryApiService', ['listOrders']);

    TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [provideRouter([]), { provide: OrdersQueryApiService, useValue: ordersApiSpy }],
    });

    fixture = TestBed.createComponent(OrderListComponent);
  }

  it('carga y muestra las órdenes visibles al iniciar', () => {
    configure();
    ordersApiSpy.listOrders.and.returnValue(of(summaries));

    fixture.detectChanges();

    expect(ordersApiSpy.listOrders).toHaveBeenCalled();
    expect(fixture.componentInstance.orders()).toEqual(summaries);
    expect(fixture.componentInstance.loading()).toBeFalse();

    const items = (fixture.nativeElement as HTMLElement).querySelectorAll('li');
    expect(items.length).toBe(2);
  });

  it('muestra un mensaje de error si la carga falla', () => {
    configure();
    const errorResponse = new HttpErrorResponse({
      status: 401,
      error: { code: 'UNAUTHORIZED', message: 'No hay sesión válida.' },
    });
    ordersApiSpy.listOrders.and.returnValue(throwError(() => errorResponse));

    fixture.detectChanges();

    expect(fixture.componentInstance.errorMessage()).toBe('No hay sesión válida.');
    expect(fixture.componentInstance.loading()).toBeFalse();
  });

  it('muestra un estado vacío cuando no hay órdenes visibles', () => {
    configure();
    ordersApiSpy.listOrders.and.returnValue(of([]));

    fixture.detectChanges();

    const empty = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(empty).toContain('No tienes órdenes visibles');
  });

  it('recarga la lista al invocar loadOrders() de nuevo', () => {
    configure();
    ordersApiSpy.listOrders.and.returnValue(of(summaries));
    fixture.detectChanges();

    fixture.componentInstance.loadOrders();

    expect(ordersApiSpy.listOrders).toHaveBeenCalledTimes(2);
  });
});
