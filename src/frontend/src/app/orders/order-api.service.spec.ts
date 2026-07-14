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
        assignedTechnicianEmail: 'tecnico@fieldops.com',
        description: 'Revisión de panel eléctrico',
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
      assignedTechnicianEmail: 'tecnico@fieldops.com',
      description: 'Revisión de panel eléctrico',
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

  it('reassignOrder hace POST a /api/v1/orders/{id}/reassignment con newTechnicianEmail', () => {
    service.reassignOrder('order-1', 'nuevo.tecnico@fieldops.com').subscribe();

    const req = httpTesting.expectOne('/api/v1/orders/order-1/reassignment');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newTechnicianEmail: 'nuevo.tecnico@fieldops.com' });
    req.flush({});
  });

  it('createOrder hace POST a /api/v1/orders con description y technicianEmail opcional', () => {
    const mockDetail: OrderDetail = {
      id: 'order-2',
      status: 'draft',
      assignedTechnicianEmail: null,
      description: 'Instalación de equipo',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      executionNote: null,
      evidencePhotoIds: [],
      rejectionComment: null,
    };

    service.createOrder('Instalación de equipo').subscribe((order) => {
      expect(order).toEqual(mockDetail);
    });

    const req = httpTesting.expectOne('/api/v1/orders');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ description: 'Instalación de equipo' });
    req.flush(mockDetail);
  });

  it('createOrder incluye technicianEmail cuando se especifica', () => {
    service.createOrder('Instalación de equipo', 'tecnico@fieldops.com').subscribe();

    const req = httpTesting.expectOne('/api/v1/orders');
    expect(req.request.body).toEqual({
      description: 'Instalación de equipo',
      technicianEmail: 'tecnico@fieldops.com',
    });
    req.flush({});
  });

  it('getEvidencePhoto hace GET a /api/v1/orders/{id}/evidence-photos/{photoId} como blob', () => {
    const mockBlob = new Blob(['contenido'], { type: 'image/jpeg' });

    service.getEvidencePhoto('order-1', 'photo-1').subscribe((blob) => {
      expect(blob).toEqual(mockBlob);
    });

    const req = httpTesting.expectOne('/api/v1/orders/order-1/evidence-photos/photo-1');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(mockBlob);
  });

  it('getIncidentSummary hace POST a /api/v1/orders/{id}/incident-summary y devuelve sufficient/summary', () => {
    service.getIncidentSummary('order-1').subscribe((result) => {
      expect(result).toEqual({ sufficient: true, summary: 'Resumen breve' });
    });

    const req = httpTesting.expectOne('/api/v1/orders/order-1/incident-summary');
    expect(req.request.method).toBe('POST');
    req.flush({ sufficient: true, summary: 'Resumen breve' });
  });

  it('changeOrderStatus hace POST a /api/v1/orders/{id}/status con status', () => {
    service.changeOrderStatus('order-1', 'in_progress').subscribe();

    const req = httpTesting.expectOne('/api/v1/orders/order-1/status');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ status: 'in_progress' });
    req.flush({});
  });

  it('getIncidentSummary propaga sufficient: false con summary null, sin tratarlo como error', () => {
    service.getIncidentSummary('order-1').subscribe((result) => {
      expect(result).toEqual({ sufficient: false, summary: null });
    });

    const req = httpTesting.expectOne('/api/v1/orders/order-1/incident-summary');
    req.flush({ sufficient: false, summary: null });
  });
});
