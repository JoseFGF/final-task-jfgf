import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ExecutionFormComponent } from './execution-form.component';
import { ExecutionApiService } from './execution-api.service';
import { OrdersQueryApiService } from './orders-query-api.service';
import { OrderDetail } from './order.model';

describe('ExecutionFormComponent', () => {
  let fixture: ComponentFixture<ExecutionFormComponent>;
  let executionApiSpy: jasmine.SpyObj<ExecutionApiService>;
  let ordersApiSpy: jasmine.SpyObj<OrdersQueryApiService>;

  const orderInProgress: OrderDetail = {
    id: 'order-1',
    status: 'in_progress',
    assignedTechnicianId: 'tech-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    executionNote: null,
    evidencePhotoIds: [],
    rejectionComment: null,
  };

  function configure(orderId: string | null = 'order-1'): void {
    executionApiSpy = jasmine.createSpyObj<ExecutionApiService>('ExecutionApiService', ['registerExecution']);
    ordersApiSpy = jasmine.createSpyObj<OrdersQueryApiService>('OrdersQueryApiService', ['getOrderDetail']);
    ordersApiSpy.getOrderDetail.and.returnValue(of(orderInProgress));

    TestBed.configureTestingModule({
      imports: [ExecutionFormComponent],
      providers: [
        provideRouter([]),
        { provide: ExecutionApiService, useValue: executionApiSpy },
        { provide: OrdersQueryApiService, useValue: ordersApiSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(orderId ? { id: orderId } : {}) },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(ExecutionFormComponent);
  }

  function selectFiles(files: File[]): void {
    const input = document.createElement('input');
    input.type = 'file';
    Object.defineProperty(input, 'files', { value: files });
    fixture.componentInstance.onFilesSelected({ target: input } as unknown as Event);
  }

  it('registra la ejecución con nota y al menos una foto (camino feliz)', () => {
    configure();
    fixture.detectChanges();

    const updated: OrderDetail = { ...orderInProgress, status: 'pending_review', executionNote: 'Listo' };
    executionApiSpy.registerExecution.and.returnValue(of(updated));

    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture.componentInstance.form.controls.note.setValue('Listo');
    selectFiles([new File(['x'], 'foto.jpg', { type: 'image/jpeg' })]);

    expect(fixture.componentInstance.canSubmit).toBe(true);

    fixture.componentInstance.submit();

    expect(executionApiSpy.registerExecution).toHaveBeenCalledWith(
      'order-1',
      'Listo',
      jasmine.arrayContaining([jasmine.any(File)]),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/orders', 'order-1']);
  });

  it('deshabilita el envío cuando no hay ninguna foto adjunta', () => {
    configure();
    fixture.detectChanges();

    fixture.componentInstance.form.controls.note.setValue('Listo, sin fotos');

    expect(fixture.componentInstance.photos().length).toBe(0);
    expect(fixture.componentInstance.canSubmit).toBe(false);

    fixture.componentInstance.submit();
    expect(executionApiSpy.registerExecution).not.toHaveBeenCalled();
  });

  it('muestra el rejectionComment de la orden cuando existe', () => {
    configure();
    ordersApiSpy.getOrderDetail.and.returnValue(
      of({ ...orderInProgress, rejectionComment: 'Falta evidencia clara de la reparación.' }),
    );

    fixture.detectChanges();

    const banner = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(banner?.textContent).toContain('Falta evidencia clara de la reparación.');
  });

  it('muestra un mensaje de error 422 cuando el backend rechaza por falta de foto válida', () => {
    configure();
    fixture.detectChanges();

    const errorResponse = new HttpErrorResponse({
      status: 422,
      error: { code: 'INVALID_EVIDENCE', message: 'No se adjuntó ninguna foto de evidencia válida.' },
    });
    executionApiSpy.registerExecution.and.returnValue(throwError(() => errorResponse));

    fixture.componentInstance.form.controls.note.setValue('Listo');
    selectFiles([new File(['x'], 'foto.jpg', { type: 'image/jpeg' })]);

    fixture.componentInstance.submit();

    expect(fixture.componentInstance.errorMessage()).toBe('No se adjuntó ninguna foto de evidencia válida.');
  });
});
