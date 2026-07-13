import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OrderApiService } from './order-api.service';
import { OrderDetail, OrderSummary } from './order.model';

describe('OrderApiService', () => {
  let service: OrderApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('listOrders hace GET a /api/v1/orders', () => {
    const mockOrders: OrderSummary[] = [
      {
        id: 'order-1',
        status: 'assigned',
        assignedTechnicianId: 'tech-1',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ];

    service.listOrders().subscribe((orders) => {
      expect(orders).toEqual(mockOrders);
    });

    const req = httpTesting.expectOne('/api/v1/orders');
    expect(req.request.method).toBe('GET');
    req.flush(mockOrders);
  });

  it('getOrderDetail hace GET a /api/v1/orders/{id}', () => {
    const mockDetail: OrderDetail = {
      id: 'order-1',
      status: 'in_progress',
      assignedTechnicianId: 'tech-1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      executionNote: null,
      evidencePhotoIds: [],
      rejectionComment: 'Falta foto del panel',
    };

    service.getOrderDetail('order-1').subscribe((order) => {
      expect(order).toEqual(mockDetail);
    });

    const req = httpTesting.expectOne('/api/v1/orders/order-1');
    expect(req.request.method).toBe('GET');
    req.flush(mockDetail);
  });

  it('registerExecution envía multipart/form-data con nota y fotos', () => {
    const photo = new File(['contenido'], 'evidencia.jpg', { type: 'image/jpeg' });

    service.registerExecution('order-1', 'Trabajo completado', [photo]).subscribe();

    const req = httpTesting.expectOne('/api/v1/orders/order-1/execution');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    const body = req.request.body as FormData;
    expect(body.get('note')).toBe('Trabajo completado');
    expect(body.get('photos')).toBeTruthy();
    req.flush({});
  });

  it('reviewOrder hace POST a /api/v1/orders/{id}/review con decision y comment', () => {
    service.reviewOrder('order-1', 'REJECT', 'Falta foto del panel').subscribe();

    const req = httpTesting.expectOne('/api/v1/orders/order-1/review');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ decision: 'REJECT', comment: 'Falta foto del panel' });
    req.flush({});
  });

  it('reviewOrder omite comment cuando decision es APPROVE', () => {
    service.reviewOrder('order-1', 'APPROVE').subscribe();

    const req = httpTesting.expectOne('/api/v1/orders/order-1/review');
    expect(req.request.body).toEqual({ decision: 'APPROVE' });
    req.flush({});
  });

  it('reassignOrder hace POST a /api/v1/orders/{id}/reassignment con newTechnicianId', () => {
    service.reassignOrder('order-1', 'tech-2').subscribe();

    const req = httpTesting.expectOne('/api/v1/orders/order-1/reassignment');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newTechnicianId: 'tech-2' });
    req.flush({});
  });

  it('getIncidentSummary hace POST a /api/v1/orders/{id}/incident-summary y devuelve sufficient/summary', () => {
    service.getIncidentSummary('order-1').subscribe((result) => {
      expect(result).toEqual({ sufficient: true, summary: 'Resumen breve' });
    });

    const req = httpTesting.expectOne('/api/v1/orders/order-1/incident-summary');
    expect(req.request.method).toBe('POST');
    req.flush({ sufficient: true, summary: 'Resumen breve' });
  });

  it('getIncidentSummary propaga sufficient: false con summary null, sin tratarlo como error', () => {
    service.getIncidentSummary('order-1').subscribe((result) => {
      expect(result).toEqual({ sufficient: false, summary: null });
    });

    const req = httpTesting.expectOne('/api/v1/orders/order-1/incident-summary');
    req.flush({ sufficient: false, summary: null });
  });
});
