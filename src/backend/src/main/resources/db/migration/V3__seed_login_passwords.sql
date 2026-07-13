-- V3__seed_login_passwords.sql
-- FR-024: sustituye el password_hash de relleno de V2 por un hash BCrypt
-- real, para que POST /api/v1/auth/login (AuthController/AuthService) tenga
-- credenciales reales contra las que autenticar a los 4 usuarios de seed.
--
-- Contraseña en texto plano para los 4 usuarios (documentada también en
-- README.md, sección "Roles de prueba (seed data)"): `password123`.
-- Hash generado con BCryptPasswordEncoder (Spring Security, factor de coste
-- por defecto = 10).
UPDATE users
SET password_hash = '$2a$10$nnkATYQ6y88UiUbKMMTriu203JKreup4obHrqJdgFg/1IXqOcbhhi'
WHERE email IN (
    'dispatcher@fieldops.test',
    'technician@fieldops.test',
    'supervisor@fieldops.test',
    'technician2@fieldops.test'
);
