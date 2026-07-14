import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { OrderDetailComponent } from './order-detail.component';
import { OrderApiService } from './order-api.service';
import { OrderDetail } from './order.model';
import { AuthService } from '../core/auth.service';

describe('OrderDetailComponent', () => {
  let fixture: ComponentFixture<OrderDetailComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const baseOrder: OrderDetail = {
    id: 'order-1',
    status: 'in_progress',
    assignedTechnicianEmail: 'tecnico@fieldops.com',
    description: 'Revisión de panel eléctrico',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    executionNote: null,
    evidencePhotoIds: [],
    rejectionComment: null,
  };

  function configure(order: OrderDetail, hasRole = false): void {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', [
      'getOrderDetail',
      'getEvidencePhoto',
    ]);
    orderApiSpy.getOrderDetail.and.returnValue(of(order));
    orderApiSpy.getEvidencePhoto.and.callFake((_orderId: string, photoId: string) =>
      of(new Blob([`contenido-${photoId}`], { type: 'image/jpeg' })),
    );

    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['hasRole']);
    authServiceSpy.hasRole.and.returnValue(hasRole);

    TestBed.configureTestingModule({
      imports: [OrderDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: OrderApiService, useValue: orderApiSpy },
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: order.id }) } },
        },
      ],
    });

    fixture = TestBed.createComponent(OrderDetailComponent);
  }

  // Se crean/revocan Object URLs reales (URL.createObjectURL) durante estos
  // tests; se stubean para no depender del entorno del navegador de test.
  let createObjectURLSpy: jasmine.Spy;

  beforeEach(() => {
    let counter = 0;
    createObjectURLSpy = spyOn(URL, 'createObjectURL').and.callFake(() => `blob:mock-${counter++}`);
    spyOn(URL, 'revokeObjectURL').and.callFake(() => undefined);
  });

  it('carga y muestra el detalle de la orden', () => {
    configure(baseOrder);

    fixture.detectChanges();

    expect(fixture.componentInstance.order()?.id).toBe('order-1');
    expect(orderApiSpy.getOrderDetail).toHaveBeenCalledWith('order-1');
  });

  it('muestra el email del técnico asignado en vez de un identificador', () => {
    configure(baseOrder);

    fixture.detectChanges();

    const technicianRow = fixture.nativeElement.textContent as string;
    expect(technicianRow).toContain('tecnico@fieldops.com');
  });

  it('destaca el rejectionComment cuando existe (FR-022)', () => {
    configure({
      ...baseOrder,
      executionNote: 'Nota original',
      rejectionComment: 'Falta foto del panel eléctrico',
    });

    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert.textContent).toContain('Falta foto del panel eléctrico');
  });

  it('canReassign() devuelve true para una orden en draft (US2, regresión)', () => {
    configure({ ...baseOrder, status: 'draft' }, true);

    fixture.detectChanges();

    expect(fixture.componentInstance.canReassign()).toBeTrue();
  });

  it('renderiza una imagen por cada foto de evidencia obtenida como blob autenticado (US4)', () => {
    configure({ ...baseOrder, evidencePhotoIds: ['photo-1', 'photo-2'] });

    fixture.detectChanges();

    expect(orderApiSpy.getEvidencePhoto).toHaveBeenCalledWith('order-1', 'photo-1');
    expect(orderApiSpy.getEvidencePhoto).toHaveBeenCalledWith('order-1', 'photo-2');

    const images = fixture.nativeElement.querySelectorAll('img');
    expect(images.length).toBe(2);
    expect(createObjectURLSpy).toHaveBeenCalledTimes(2);
  });

  it('no muestra ninguna sección de evidencia fotográfica cuando no hay fotos', () => {
    configure({ ...baseOrder, evidencePhotoIds: [] });

    fixture.detectChanges();

    expect(orderApiSpy.getEvidencePhoto).not.toHaveBeenCalled();
    const images = fixture.nativeElement.querySelectorAll('img');
    expect(images.length).toBe(0);
    expect(fixture.nativeElement.textContent).not.toContain('Evidencia fotográfica');
  });
});
