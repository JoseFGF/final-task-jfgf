import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { JwtPayload, LoginResponse, UserRole } from './auth.model';
import { decodeJwtPayload, isJwtExpired } from './jwt.util';

const TOKEN_STORAGE_KEY = 'fieldops.jwt';
const LOGIN_URL = '/api/v1/auth/login';

/**
 * Guarda y expone el JWT de sesión, y realiza el login contra el backend
 * (`POST /api/v1/auth/login`). El estado vive en un signal en memoria e
 * hidrata desde `localStorage` al arrancar la app, para sobrevivir a
 * recargas de página.
 *
 * La verificación real de validez del token (firma, expiración estricta) la
 * hace siempre el backend; aquí solo se usa para decisiones de UX. El
 * control de acceso real de cada endpoint lo aplica el servidor.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly tokenSignal = signal<string | null>(this.readStoredToken());

  private readonly payloadSignal = computed<JwtPayload | null>(() => {
    const token = this.tokenSignal();
    return token ? decodeJwtPayload(token) : null;
  });

  /** `true` si hay un JWT presente y no expirado (solo control de UX). */
  readonly isAuthenticated = computed<boolean>(() => {
    const payload = this.payloadSignal();
    return payload !== null && !isJwtExpired(payload);
  });

  /** Rol decodificado del token de sesión actual, o `null` si no hay sesión válida. */
  readonly role = computed<UserRole | null>(() => {
    const payload = this.payloadSignal();
    return this.isAuthenticated() ? (payload?.role ?? null) : null;
  });

  /**
   * `POST /api/v1/auth/login`. Guarda automáticamente el token recibido en
   * caso de éxito. El backend es la autoridad final sobre la validez de las
   * credenciales (401 en caso contrario).
   */
  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(LOGIN_URL, { email, password })
      .pipe(tap((response) => this.setToken(response.token)));
  }

  /** Guarda el JWT recibido (memoria + `localStorage`). */
  setToken(token: string): void {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
    this.tokenSignal.set(token);
  }

  /** Token JWT actual, o `null` si no hay sesión. */
  getToken(): string | null {
    return this.tokenSignal();
  }

  /** Elimina el JWT de sesión. */
  clearToken(): void {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    this.tokenSignal.set(null);
  }

  /** Cierra la sesión actual (alias semántico de `clearToken`). */
  logout(): void {
    this.clearToken();
  }

  /** `true` si el rol de sesión actual está entre los permitidos. */
  hasRole(...allowedRoles: readonly UserRole[]): boolean {
    const currentRole = this.role();
    return currentRole !== null && allowedRoles.includes(currentRole);
  }

  private readStoredToken(): string | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }
    return localStorage.getItem(TOKEN_STORAGE_KEY);
  }
}
