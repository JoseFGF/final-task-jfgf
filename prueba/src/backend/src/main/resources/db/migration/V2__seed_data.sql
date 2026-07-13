-- V2__seed_data.sql
-- Datos de prueba: un dispatcher, dos technicians y un supervisor, con
-- password real (FR-024) para poder autenticarse desde el principio contra
-- POST /api/v1/auth/login; y 5 órdenes cubriendo cada estado de la máquina
-- de estados de data-model.md. IDs fijos para que tests puedan referenciarlos
-- de forma determinista.
--
-- Contraseña en texto plano para los 4 usuarios: `password123`. Hash
-- generado con BCryptPasswordEncoder (Spring Security, factor de coste por
-- defecto = 10).

INSERT INTO users (id, email, password_hash, role, full_name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'dispatcher@prueba.test', '$2a$10$nnkATYQ6y88UiUbKMMTriu203JKreup4obHrqJdgFg/1IXqOcbhhi', 'DISPATCHER', 'Dana Dispatcher'),
    ('22222222-2222-2222-2222-222222222222', 'technician@prueba.test', '$2a$10$nnkATYQ6y88UiUbKMMTriu203JKreup4obHrqJdgFg/1IXqOcbhhi', 'TECHNICIAN', 'Tomás Technician'),
    ('33333333-3333-3333-3333-333333333333', 'supervisor@prueba.test', '$2a$10$nnkATYQ6y88UiUbKMMTriu203JKreup4obHrqJdgFg/1IXqOcbhhi', 'SUPERVISOR', 'Sara Supervisor'),
    ('44444444-4444-4444-4444-444444444444', 'technician2@prueba.test', '$2a$10$nnkATYQ6y88UiUbKMMTriu203JKreup4obHrqJdgFg/1IXqOcbhhi', 'TECHNICIAN', 'Tania Technician');

-- draft: sin technician asignado todavía (creación/asignación inicial fuera
-- de alcance de esta base común).
INSERT INTO orders (id, status, assigned_technician_id) VALUES
    ('a1111111-1111-1111-1111-111111111111', 'draft', NULL);

-- assigned: entregada al technician, aún sin empezar.
INSERT INTO orders (id, status, assigned_technician_id) VALUES
    ('a2222222-2222-2222-2222-222222222222', 'assigned', '22222222-2222-2222-2222-222222222222');

-- in_progress: para poder probar el registro de ejecución sobre el
-- technician de seed.
INSERT INTO orders (id, status, assigned_technician_id) VALUES
    ('a3333333-3333-3333-3333-333333333333', 'in_progress', '22222222-2222-2222-2222-222222222222');

-- pending_review: con ejecución ya registrada (nota + foto), lista para que
-- el supervisor apruebe/rechace.
INSERT INTO orders (id, status, assigned_technician_id, execution_note) VALUES
    ('a4444444-4444-4444-4444-444444444444', 'pending_review', '22222222-2222-2222-2222-222222222222',
     'Se reemplazó el conector dañado y se verificó el suministro eléctrico; el equipo quedó operativo.');

INSERT INTO evidence_photos (id, order_id, storage_path, content_type, size_bytes) VALUES
    ('b1111111-1111-1111-1111-111111111111', 'a4444444-4444-4444-4444-444444444444',
     '/data/evidence/a4444444/photo-1.jpg', 'image/jpeg', 245760);

-- closed: ciclo completo ya aprobado por el supervisor.
INSERT INTO orders (id, status, assigned_technician_id, execution_note) VALUES
    ('a5555555-5555-5555-5555-555555555555', 'closed', '44444444-4444-4444-4444-444444444444',
     'Mantenimiento preventivo completado sin incidencias.');

INSERT INTO evidence_photos (id, order_id, storage_path, content_type, size_bytes) VALUES
    ('b2222222-2222-2222-2222-222222222222', 'a5555555-5555-5555-5555-555555555555',
     '/data/evidence/a5555555/photo-1.jpg', 'image/jpeg', 198340);
