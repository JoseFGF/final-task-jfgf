import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { OrdersQueryApiService } from './orders-query-api.service';
import { OrderDetail, OrderSummary } from './order.model';

describe('OrdersQueryApiService', () => {
  let service: OrdersQueryApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrdersQueryApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lista órdenes con GET /api/v1/orders', () => {
    const summaries: OrderSummary[] = [
      {
        id: 'order-1',
        status: 'assigned',
        assignedTechnicianId: 'tech-1',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ];

    let result: OrderSummary[] | undefined;
    service.listOrders().subscribe((response) => (result = response));

    const req = httpMock.expectOne('/api/v1/orders');
    expect(req.request.method).toBe('GET');
    req.flush(summaries);

    expect(result).toEqual(summaries);
  });

  it('obtiene el detalle con GET /api/v1/orders/{orderId}', () => {
    const detail: OrderDetail = {
      id: 'order-1',
      status: 'in_progress',
      assignedTechnicianId: 'tech-1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
      executionNote: null,
      evidencePhotoIds: [],
      rejectionComment: 'Falta evidencia clara de la reparación.',
    };

    let result: OrderDetail | undefined;
    service.getOrderDetail('order-1').subscribe((response) => (result = response));

    const req = httpMock.expectOne('/api/v1/orders/order-1');
    expect(req.request.method).toBe('GET');
    req.flush(detail);

    expect(result).toEqual(detail);
  });
});
