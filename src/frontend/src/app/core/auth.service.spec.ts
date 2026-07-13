import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

function buildToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
  });

  afterEach(() => {
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
});
