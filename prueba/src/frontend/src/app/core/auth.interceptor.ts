import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { AuthService } from './auth.service';

/**
 * Adjunta el JWT de sesión (si existe) como header `Authorization: Bearer
 * <token>` a toda petición saliente. El backend sigue siendo quien decide
 * si ese token es válido y si el rol que contiene autoriza la acción
 * solicitada.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  if (!token) {
    return next(req);
  }

  const authorizedRequest = req.clone({
    setHeaders: { Authorization: `Bearer ${token}` },
  });

  return next(authorizedRequest);
};
