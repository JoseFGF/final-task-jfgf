import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

function buildToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('no autenticado por defecto', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.role()).toBeNull();
  });

  it('setToken autentica y expone el rol decodificado', () => {
    const token = buildToken({ sub: 'u1', role: 'SUPERVISOR', exp: 9999999999 });
    service.setToken(token);

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.role()).toBe('SUPERVISOR');
    expect(service.hasRole('SUPERVISOR')).toBeTrue();
    expect(service.hasRole('TECHNICIAN')).toBeFalse();
  });

  it('un token expirado no autentica', () => {
    const token = buildToken({ sub: 'u1', role: 'DISPATCHER', exp: 1 });
    service.setToken(token);

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.role()).toBeNull();
  });

  it('clearToken cierra la sesión', () => {
    const token = buildToken({ sub: 'u1', role: 'TECHNICIAN', exp: 9999999999 });
    service.setToken(token);
    service.clearToken();

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getToken()).toBeNull();
  });

  it('logout es un alias de clearToken', () => {
    const token = buildToken({ sub: 'u1', role: 'TECHNICIAN', exp: 9999999999 });
    service.setToken(token);
    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
  });

  it('login hace POST a /api/v1/auth/login y guarda el token recibido', () => {
    service.login('user@example.com', 'secret').subscribe();

    const req = httpTesting.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'user@example.com', password: 'secret' });

    req.flush({ token: 'a.b.c' });

    expect(service.getToken()).toBe('a.b.c');
  });

  it('hidrata el token desde localStorage al construirse', () => {
    const token = buildToken({ sub: 'u1', role: 'DISPATCHER', exp: 9999999999 });
    localStorage.setItem('fieldops.jwt', token);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const rehydrated = TestBed.inject(AuthService);

    expect(rehydrated.getToken()).toBe(token);
    expect(rehydrated.isAuthenticated()).toBeTrue();
  });
});
