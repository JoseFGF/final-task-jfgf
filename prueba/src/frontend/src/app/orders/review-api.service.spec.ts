import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ReviewApiService } from './review-api.service';
import { OrderDetail } from './order.model';

describe('ReviewApiService', () => {
  let service: ReviewApiService;
  let httpMock: HttpTestingController;

  const baseOrder: OrderDetail = {
    id: 'order-1',
    status: 'pending_review',
    assignedTechnicianId: 'tech-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    executionNote: 'Trabajo completado',
    evidencePhotoIds: ['photo-1'],
    rejectionComment: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReviewApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('envía POST con decision APPROVE a /api/v1/orders/{orderId}/review', () => {
    const closed: OrderDetail = { ...baseOrder, status: 'closed' };

    let result: OrderDetail | undefined;
    service.reviewOrder('order-1', 'APPROVE').subscribe((response) => (result = response));

    const req = httpMock.expectOne('/api/v1/orders/order-1/review');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ decision: 'APPROVE' });
    req.flush(closed);

    expect(result).toEqual(closed);
  });

  it('envía POST con decision REJECT y comment a /api/v1/orders/{orderId}/review', () => {
    const rejected: OrderDetail = { ...baseOrder, status: 'in_progress', rejectionComment: 'Falta foto clara.' };

    let result: OrderDetail | undefined;
    service.reviewOrder('order-1', 'REJECT', 'Falta foto clara.').subscribe((response) => (result = response));

    const req = httpMock.expectOne('/api/v1/orders/order-1/review');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ decision: 'REJECT', comment: 'Falta foto clara.' });
    req.flush(rejected);

    expect(result).toEqual(rejected);
  });
});
