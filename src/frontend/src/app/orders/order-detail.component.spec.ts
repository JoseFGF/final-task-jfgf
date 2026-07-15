import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, Subject } from 'rxjs';
import { OrderDetailComponent } from './order-detail.component';
import { OrderApiService } from './order-api.service';
import { OrderDetail, OrderStatus } from './order.model';
import { AuthService } from '../core/auth.service';
import { UserRole } from '../core/auth.model';

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

  function configure(
    order: OrderDetail,
    hasRole: boolean | ((...roles: UserRole[]) => boolean) = false,
  ): void {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', [
      'getOrderDetail',
      'getEvidencePhoto',
      'changeOrderStatus',
    ]);
    orderApiSpy.getOrderDetail.and.returnValue(of(order));
    orderApiSpy.getEvidencePhoto.and.callFake((_orderId: string, photoId: string) =>
      of(new Blob([`contenido-${photoId}`], { type: 'image/jpeg' })),
    );
    orderApiSpy.changeOrderStatus.and.returnValue(of(order));

    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['hasRole']);
    if (typeof hasRole === 'function') {
      authServiceSpy.hasRole.and.callFake(hasRole);
    } else {
      authServiceSpy.hasRole.and.returnValue(hasRole);
    }

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

  describe('"Iniciar trabajo" (US1, T011)', () => {
    it('aparece para TECHNICIAN sobre una orden assigned y llama a changeOrderStatus al pulsarlo', () => {
      configure({ ...baseOrder, status: 'assigned' }, (role) => role === 'TECHNICIAN');

      fixture.detectChanges();

      expect(fixture.componentInstance.canStartWork()).toBeTrue();
      const button = Array.from<HTMLButtonElement>(
        fixture.nativeElement.querySelectorAll('button'),
      ).find((el) => el.textContent?.includes('Iniciar trabajo'));
      expect(button).toBeTruthy();

      button!.click();

      expect(orderApiSpy.changeOrderStatus).toHaveBeenCalledWith('order-1', 'in_progress');
      expect(orderApiSpy.getOrderDetail).toHaveBeenCalledTimes(2);
    });

    it('no aparece para TECHNICIAN si la orden no está assigned', () => {
      configure({ ...baseOrder, status: 'in_progress' }, (role) => role === 'TECHNICIAN');

      fixture.detectChanges();

      expect(fixture.componentInstance.canStartWork()).toBeFalse();
      expect(fixture.nativeElement.textContent).not.toContain('Iniciar trabajo');
    });

    it('no aparece para un rol distinto de TECHNICIAN aunque la orden esté assigned', () => {
      configure({ ...baseOrder, status: 'assigned' }, (role) => role === 'DISPATCHER');

      fixture.detectChanges();

      expect(fixture.componentInstance.canStartWork()).toBeFalse();
      expect(fixture.nativeElement.textContent).not.toContain('Iniciar trabajo');
    });

    it('deshabilita el botón mientras la petición está en curso y lo reactiva al terminar', () => {
      configure({ ...baseOrder, status: 'assigned' }, (role) => role === 'TECHNICIAN');
      const statusChange$ = new Subject<OrderDetail>();
      orderApiSpy.changeOrderStatus.and.returnValue(statusChange$);

      fixture.detectChanges();
      const button = Array.from<HTMLButtonElement>(
        fixture.nativeElement.querySelectorAll('button'),
      ).find((el) => el.textContent?.includes('Iniciar trabajo'))!;

      button.click();
      fixture.detectChanges();
      expect(button.disabled).toBeTrue();

      statusChange$.next({ ...baseOrder, status: 'in_progress' });
      statusChange$.complete();
      fixture.detectChanges();

      expect(fixture.componentInstance.statusChangeSubmitting()).toBeFalse();
    });
  });

  describe('Corrección manual de estado (US2, T022b)', () => {
    const cases: { status: OrderStatus; expected: OrderStatus[] }[] = [
      { status: 'assigned', expected: ['draft', 'in_progress'] },
      { status: 'in_progress', expected: ['assigned', 'pending_review'] },
      { status: 'pending_review', expected: ['in_progress', 'closed'] },
    ];

    for (const { status, expected } of cases) {
      it(`ofrece exactamente los destinos adyacentes esperados desde ${status} para DISPATCHER/SUPERVISOR`, () => {
        configure({ ...baseOrder, status }, (role) => role === 'DISPATCHER' || role === 'SUPERVISOR');

        fixture.detectChanges();

        expect(fixture.componentInstance.manualStatusOptions()).toEqual(expected);
        expect(fixture.componentInstance.canChangeStatusManually()).toBeTrue();

        for (const target of expected) {
          expect(fixture.nativeElement.textContent).toContain(`Mover a ${target}`);
        }
      });
    }

    it('llama a changeOrderStatus con el destino elegido y recarga la orden', () => {
      configure({ ...baseOrder, status: 'in_progress' }, (role) => role === 'DISPATCHER');

      fixture.detectChanges();
      fixture.componentInstance.changeStatusManually('pending_review');

      expect(orderApiSpy.changeOrderStatus).toHaveBeenCalledWith('order-1', 'pending_review');
      expect(orderApiSpy.getOrderDetail).toHaveBeenCalledTimes(2);
    });

    it('no aparece para un TECHNICIAN', () => {
      configure({ ...baseOrder, status: 'assigned' }, (role) => role === 'TECHNICIAN');

      fixture.detectChanges();

      expect(fixture.componentInstance.canChangeStatusManually()).toBeFalse();
      expect(fixture.nativeElement.textContent).not.toContain('Corrección manual de estado');
    });

    it('no aparece en absoluto sobre una orden closed, aunque el rol sea DISPATCHER/SUPERVISOR', () => {
      configure({ ...baseOrder, status: 'closed' }, (role) => role === 'DISPATCHER' || role === 'SUPERVISOR');

      fixture.detectChanges();

      expect(fixture.componentInstance.manualStatusOptions()).toEqual([]);
      expect(fixture.componentInstance.canChangeStatusManually()).toBeFalse();
      expect(fixture.nativeElement.textContent).not.toContain('Corrección manual de estado');
    });
  });
});
