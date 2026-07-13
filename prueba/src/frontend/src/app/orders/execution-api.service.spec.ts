import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ExecutionApiService } from './execution-api.service';
import { OrderDetail } from './order.model';

describe('ExecutionApiService', () => {
  let service: ExecutionApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ExecutionApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('envía POST multipart con nota y fotos a /api/v1/orders/{orderId}/execution', () => {
    const detail: OrderDetail = {
      id: 'order-1',
      status: 'pending_review',
      assignedTechnicianId: 'tech-1',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
      executionNote: 'Trabajo completado',
      evidencePhotoIds: ['photo-1'],
      rejectionComment: null,
    };
    const photo = new File(['data'], 'evidencia.jpg', { type: 'image/jpeg' });

    let result: OrderDetail | undefined;
    service.registerExecution('order-1', 'Trabajo completado', [photo]).subscribe((response) => (result = response));

    const req = httpMock.expectOne('/api/v1/orders/order-1/execution');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    const body = req.request.body as FormData;
    expect(body.get('note')).toBe('Trabajo completado');
    expect(body.getAll('photos').length).toBe(1);
    req.flush(detail);

    expect(result).toEqual(detail);
  });
});
