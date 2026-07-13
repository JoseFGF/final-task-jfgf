import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ReassignmentApiService } from './reassignment-api.service';
import { OrderDetail } from './order.model';

describe('ReassignmentApiService', () => {
  let service: ReassignmentApiService;
  let httpMock: HttpTestingController;

  const baseOrder: OrderDetail = {
    id: 'order-1',
    status: 'assigned',
    assignedTechnicianId: 'tech-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    executionNote: null,
    evidencePhotoIds: [],
    rejectionComment: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReassignmentApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('envía POST con newTechnicianId a /api/v1/orders/{orderId}/reassignment', () => {
    const reassigned: OrderDetail = { ...baseOrder, assignedTechnicianId: 'tech-2' };

    let result: OrderDetail | undefined;
    service.reassignOrder('order-1', 'tech-2').subscribe((response) => (result = response));

    const req = httpMock.expectOne('/api/v1/orders/order-1/reassignment');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newTechnicianId: 'tech-2' });
    req.flush(reassigned);

    expect(result).toEqual(reassigned);
  });
});
