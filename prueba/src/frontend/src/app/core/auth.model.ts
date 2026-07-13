/**
 * Roles reconocidos por el dominio de FieldOps (contracts/openapi.yaml, x-roles).
 */
export type UserRole = 'DISPATCHER' | 'TECHNICIAN' | 'SUPERVISOR';

/**
 * Forma esperada del payload del JWT emitido por el backend: el rol viaja
 * como claim junto a los campos estándar de expiración/emisión.
 */
export interface JwtPayload {
  sub: string;
  role: UserRole;
  exp: number;
  iat?: number;
}

/** Respuesta de `POST /api/v1/auth/login`. */
export interface LoginResponse {
  token: string;
}

/**
 * Forma común de los errores devueltos por la API (`ErrorResponse` en
 * `contracts/openapi.yaml`), usada por cualquier feature que consuma la API.
 */
export interface ApiErrorResponse {
  code: string;
  message: string;
}
