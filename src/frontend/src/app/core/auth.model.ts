/**
 * Roles reconocidos por el dominio de FieldOps (contracts/openapi.yaml, x-roles).
 */
export type UserRole = 'DISPATCHER' | 'TECHNICIAN' | 'SUPERVISOR';

/**
 * Forma esperada del claim del JWT emitido por el backend (ADR-002,
 * research.md): el rol viaja como claim junto a los campos estándar.
 */
export interface JwtPayload {
  sub: string;
  role: UserRole;
  exp: number;
  iat?: number;
}

/** Respuesta de `POST /api/v1/auth/login` (FR-024). */
export interface LoginResponse {
  token: string;
}
