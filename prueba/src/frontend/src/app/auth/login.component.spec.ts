import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../core/auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  function configure(): void {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['login']);

    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: authServiceSpy }],
    });

    fixture = TestBed.createComponent(LoginComponent);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  }

  it('inicia sesión y navega a /orders en el camino feliz', () => {
    configure();
    authServiceSpy.login.and.returnValue(of({ token: 'jwt-token' }));
    spyOn(router, 'navigate').and.resolveTo(true);

    const component = fixture.componentInstance;
    component.form.controls.email.setValue('user@example.com');
    component.form.controls.password.setValue('secret');

    expect(component.canSubmit).toBeTrue();
    component.submit();

    expect(authServiceSpy.login).toHaveBeenCalledWith('user@example.com', 'secret');
    expect(router.navigate).toHaveBeenCalledWith(['/orders']);
  });

  it('muestra el error 401 del backend con credenciales inválidas', () => {
    configure();
    const errorResponse = new HttpErrorResponse({
      status: 401,
      error: { code: 'INVALID_CREDENTIALS', message: 'Email o contraseña incorrectos.' },
    });
    authServiceSpy.login.and.returnValue(throwError(() => errorResponse));

    const component = fixture.componentInstance;
    component.form.controls.email.setValue('user@example.com');
    component.form.controls.password.setValue('wrong-password');
    component.submit();

    expect(component.errorMessage()).toBe('Email o contraseña incorrectos.');
    expect(component.submitting()).toBeFalse();
  });

  it('no permite enviar con el formulario inválido', () => {
    configure();

    const component = fixture.componentInstance;
    component.form.controls.email.setValue('no-es-un-email');
    component.form.controls.password.setValue('');

    expect(component.canSubmit).toBeFalse();
    component.submit();

    expect(authServiceSpy.login).not.toHaveBeenCalled();
  });

  it('renderiza el mensaje de error en el template', () => {
    configure();
    const errorResponse = new HttpErrorResponse({ status: 401, error: null });
    authServiceSpy.login.and.returnValue(throwError(() => errorResponse));

    const component = fixture.componentInstance;
    component.form.controls.email.setValue('user@example.com');
    component.form.controls.password.setValue('wrong-password');
    component.submit();
    fixture.detectChanges();

    const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alert?.textContent?.trim()).toContain('Email o contraseña incorrectos.');
  });
});
