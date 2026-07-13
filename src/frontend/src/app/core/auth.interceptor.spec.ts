import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpTesting: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('no agrega Authorization si no hay token', () => {
    httpClient.get('/api/v1/orders').subscribe();
    const req = httpTesting.expectOne('/api/v1/orders');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush([]);
  });

  it('agrega el header Authorization con Bearer <token> si hay sesión', () => {
    authService.setToken('a.b.c');
    httpClient.get('/api/v1/orders').subscribe();
    const req = httpTesting.expectOne('/api/v1/orders');
    expect(req.request.headers.get('Authorization')).toBe('Bearer a.b.c');
    req.flush([]);
  });
});
