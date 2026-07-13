import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { IncidentSummaryComponent } from './incident-summary.component';
import { OrderApiService } from './order-api.service';
import { IncidentSummaryResult } from './order.model';

describe('IncidentSummaryComponent', () => {
  let fixture: ComponentFixture<IncidentSummaryComponent>;
  let orderApiSpy: jasmine.SpyObj<OrderApiService>;

  function configure(): void {
    orderApiSpy = jasmine.createSpyObj<OrderApiService>('OrderApiService', ['getIncidentSummary']);

    TestBed.configureTestingModule({
      imports: [IncidentSummaryComponent],
      providers: [{ provide: OrderApiService, useValue: orderApiSpy }],
    });

    fixture = TestBed.createComponent(IncidentSummaryComponent);
    fixture.componentRef.setInput('orderId', 'order-1');
    fixture.detectChanges();
  }

  it('muestra el resumen cuando sufficient es true (FR-015)', () => {
    configure();
    const response: IncidentSummaryResult = {
      sufficient: true,
      summary: 'El técnico reportó una fuga menor ya resuelta en el panel principal.',
    };
    orderApiSpy.getIncidentSummary.and.returnValue(of(response));

    fixture.componentInstance.requestSummary();

    expect(orderApiSpy.getIncidentSummary).toHaveBeenCalledWith('order-1');
    expect(fixture.componentInstance.result()).toEqual(response);
    expect(fixture.componentInstance.errorMessage()).toBeNull();

    fixture.detectChanges();
    const summaryText = fixture.nativeElement.textContent as string;
    expect(summaryText).toContain('fuga menor');
    expect(summaryText).not.toContain('evidencia suficiente');
  });

  it('muestra un aviso explícito de evidencia insuficiente cuando sufficient es false, sin inventar un resumen (FR-016)', () => {
    configure();
    const response: IncidentSummaryResult = { sufficient: false, summary: null };
    orderApiSpy.getIncidentSummary.and.returnValue(of(response));

    fixture.componentInstance.requestSummary();

    expect(fixture.componentInstance.result()).toEqual(response);
    expect(fixture.componentInstance.errorMessage()).toBeNull();

    fixture.detectChanges();
    const alertElement: HTMLElement | null = fixture.nativeElement.querySelector('[role="status"].bg-amber-50');
    expect(alertElement).not.toBeNull();
    expect(alertElement?.textContent).toContain('No hay evidencia suficiente');
  });

  it('distingue un fallo de red del caso de evidencia insuficiente', () => {
    configure();
    const errorResponse = new HttpErrorResponse({
      status: 409,
      error: { code: 'INVALID_STATE', message: 'La orden no está en revisión pendiente.' },
    });
    orderApiSpy.getIncidentSummary.and.returnValue(throwError(() => errorResponse));

    fixture.componentInstance.requestSummary();

    expect(fixture.componentInstance.result()).toBeNull();
    expect(fixture.componentInstance.errorMessage()).toBe('La orden no está en revisión pendiente.');
  });
});
