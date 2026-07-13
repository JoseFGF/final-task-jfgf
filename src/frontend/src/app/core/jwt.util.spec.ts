import { decodeJwtPayload, isJwtExpired } from './jwt.util';

function buildToken(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('jwt.util', () => {
  it('decodeJwtPayload devuelve el payload de un JWT válido', () => {
    const token = buildToken({ sub: 'user-1', role: 'TECHNICIAN', exp: 9999999999 });
    const payload = decodeJwtPayload(token);
    expect(payload?.sub).toBe('user-1');
    expect(payload?.role).toBe('TECHNICIAN');
  });

  it('decodeJwtPayload devuelve null para un token mal formado', () => {
    expect(decodeJwtPayload('no-es-un-jwt')).toBeNull();
  });

  it('isJwtExpired es true cuando exp ya pasó', () => {
    const payload = { sub: 'u', role: 'DISPATCHER' as const, exp: 1 };
    expect(isJwtExpired(payload)).toBeTrue();
  });

  it('isJwtExpired es false cuando exp está en el futuro', () => {
    const payload = { sub: 'u', role: 'DISPATCHER' as const, exp: 9999999999 };
    expect(isJwtExpired(payload)).toBeFalse();
  });

  it('isJwtExpired es true cuando el payload es null', () => {
    expect(isJwtExpired(null)).toBeTrue();
  });
});
