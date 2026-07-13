import { JwtPayload } from './auth.model';

/**
 * Decodifica el payload de un JWT (base64url) sin verificar su firma: la
 * verificación de la firma es responsabilidad exclusiva del backend. Esta
 * función solo se usa para decisiones de UX (mostrar/ocultar acciones según
 * rol, saber si conviene mandar al usuario a /login). Devuelve `null` si el
 * token no tiene forma de JWT o el payload no es JSON válido.
 */
export function decodeJwtPayload(token: string): JwtPayload | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }

  try {
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

/** `true` si el token ya expiró, o si no se pudo leer un `exp` válido. */
export function isJwtExpired(payload: JwtPayload | null): boolean {
  if (!payload?.exp) {
    return true;
  }
  const nowInSeconds = Date.now() / 1000;
  return payload.exp <= nowInSeconds;
}
