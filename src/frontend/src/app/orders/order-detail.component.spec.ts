import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { OrderDetailComponent } from './order-detail.component';
import { OrderApiService } from './order-api.service';
import { OrderDetail } from './order.model';

describe('OrderDetailComponent', () => {
  let fixture: ComponentFixture<OrderDetailComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;

  function configure(order: OrderDetail): void {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', ['getOrderDetail']);
    orderApiSpy.getOrderDetail.and.returnValue(of(order));

    TestBed.configureTestingModule({
      imports: [OrderDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderApiService, useValue: orderApiSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: order.id }) } },
        },
      ],
    });

    fixture = TestBed.createComponent(OrderDetailComponent);
  }

  it('carga y muestra el detalle de la orden', () => {
    configure({
      id: 'order-1',
      status: 'in_progress',
      assignedTechnicianId: 'tech-1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      executionNote: null,
      evidencePhotoIds: [],
      rejectionComment: null,
    });

    fixture.detectChanges();

    expect(fixture.componentInstance.order()?.id).toBe('order-1');
    expect(orderApiSpy.getOrderDetail).toHaveBeenCalledWith('order-1');
  });

  it('destaca el rejectionComment cuando existe (FR-022)', () => {
    configure({
      id: 'order-1',
      status: 'in_progress',
      assignedTechnicianId: 'tech-1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      executionNote: 'Nota original',
      evidencePhotoIds: [],
      rejectionComment: 'Falta foto del panel eléctrico',
    });

    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('Falta foto del panel eléctrico');
  });
});
