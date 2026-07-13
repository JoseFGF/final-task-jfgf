import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OrderListComponent } from './order-list.component';
import { OrderApiService } from './order-api.service';
import { OrderSummary } from './order.model';

describe('OrderListComponent', () => {
  let fixture: ComponentFixture<OrderListComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;

  const mockOrders: OrderSummary[] = [
    {
      id: 'order-1',
      status: 'in_progress',
      assignedTechnicianId: 'tech-1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', ['listOrders']);

    await TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [provideRouter([]), { provide: OrderApiService, useValue: orderApiSpy }],
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
});
