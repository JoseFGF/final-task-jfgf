import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { UserRole } from './auth.model';

/**
 * Bloquea la navegación a rutas protegidas si no hay un JWT válido en
 * sesión. Esto es únicamente una comprobación de UX: la autorización real de
 * cada petición la valida el backend, incluso si esta guard se saltara o se
 * manipulara desde el cliente.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  return router.parseUrl('/login');
};

/**
 * Variante para `canMatch`, útil para ocultar rutas completas (p.ej. lazy
 * chunks) cuando no hay sesión válida, en vez de solo bloquear la
 * activación.
 */
export const authCanMatchGuard: CanMatchFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  return router.parseUrl('/login');
};

/**
 * Fábrica de guard por rol, para uso en rutas específicas de
 * dispatcher/technician/supervisor. Requiere sesión válida y que el rol de
 * sesión esté entre los permitidos; de lo contrario redirige a la raíz.
 *
 * Igual que `authGuard`, este control es de UX: el backend rechaza con 403
 * cualquier petición que no corresponda al rol, la haya dejado pasar o no
 * esta guard.
 */
export function roleGuard(...allowedRoles: readonly UserRole[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      return router.parseUrl('/login');
    }

    if (authService.hasRole(...allowedRoles)) {
      return true;
    }

    return router.parseUrl('/');
  };
}
