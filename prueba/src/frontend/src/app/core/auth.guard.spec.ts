import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideRouter } from '@angular/router';
import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { authGuard, roleGuard } from './auth.guard';
import { AuthService } from './auth.service';

function buildToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

@Component({ standalone: true, template: '' })
class DummyComponent {}

describe('auth guards', () => {
  let authService: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'protected', component: DummyComponent, canActivate: [authGuard] },
          {
            path: 'supervisor-only',
            component: DummyComponent,
            canActivate: [roleGuard('SUPERVISOR')],
          },
          { path: 'login', component: DummyComponent },
          { path: '', component: DummyComponent },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('authGuard redirige a /login sin sesión', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/protected');
    const router = TestBed.inject(Router);
    expect(router.url).toBe('/login');
  });

  it('authGuard permite navegar con sesión válida', async () => {
    authService.setToken(buildToken({ sub: 'u1', role: 'DISPATCHER', exp: 9999999999 }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/protected');
    const router = TestBed.inject(Router);
    expect(router.url).toBe('/protected');
  });

  it('roleGuard redirige a /login sin sesión', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/supervisor-only');
    const router = TestBed.inject(Router);
    expect(router.url).toBe('/login');
  });

  it('roleGuard rechaza un rol no permitido', async () => {
    authService.setToken(buildToken({ sub: 'u1', role: 'TECHNICIAN', exp: 9999999999 }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/supervisor-only');
    const router = TestBed.inject(Router);
    expect(router.url).toBe('/');
  });

  it('roleGuard permite el rol permitido', async () => {
    authService.setToken(buildToken({ sub: 'u1', role: 'SUPERVISOR', exp: 9999999999 }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/supervisor-only');
    const router = TestBed.inject(Router);
    expect(router.url).toBe('/supervisor-only');
  });
});
