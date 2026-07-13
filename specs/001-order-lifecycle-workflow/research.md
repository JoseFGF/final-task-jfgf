# Research: Ciclo de Vida de Órdenes — Reasignación, Ejecución y Revisión

Decisiones técnicas tomadas durante Plan, con alternativa descartada y motivo,
conforme al Principio VIII de la constitution (ADR con criterio de revisión).

## ADR-001: Proveedor del componente de IA

- **Decision**: API de Anthropic (Claude) invocada vía HTTP desde el backend
  Spring Boot, con la API key gestionada como secreto de entorno (nunca en
  código ni en el repositorio).
- **Rationale**: Confirmado explícitamente por el usuario. Calidad de resumen
  y de detección de "evidencia insuficiente" superior a una heurística local,
  sin necesidad de entrenar/mantener un modelo propio.
- **Alternatives considered**: (a) API de OpenAI — equivalente en
  implicaciones, descartada por preferencia explícita del usuario por
  Anthropic; (b) heurística/extractiva local sin LLM — descartada por dar un
  resumen y una detección de insuficiencia demasiado toscos para el objetivo
  del brief ("que el supervisor no tenga que leérselo todo").
- **Revisar si**: cambia el volumen de peticiones hasta hacer el coste por
  request significativo, o si la API deja de estar disponible/soportada.
- **Riesgo asociado (fail-safe)**: si la llamada a la API falla o hace
  timeout, el sistema DEBE devolver el mismo resultado que el caso de
  "evidencia insuficiente" (Principio V) — nunca debe bloquear la revisión del
  supervisor ni inventar un resumen de repuesto.

## ADR-002: Mecanismo de autenticación y RBAC

- **Decision**: Spring Security con autenticación basada en JWT (stateless);
  el token incluye el rol del usuario como claim, verificado en cada endpoint
  vía `@PreAuthorize`.
- **Rationale**: El frontend Angular y el backend Spring Boot son aplicaciones
  desacopladas (SPA + API REST); JWT evita la complejidad de sesiones de
  servidor con cookies entre dominios/orígenes distintos, y es el patrón
  estándar para esta arquitectura.
- **Alternatives considered**: sesión de servidor con cookie — descartada por
  añadir complejidad de CORS/cookies entre frontend y backend sin necesidad
  real para un slice de este tamaño.
- **Revisar si**: se añade un requisito de revocación de sesión inmediata
  (los JWT no revocados expiran solos, no se invalidan al vuelo) — hoy no está
  pedido por el brief.

## ADR-003: Base de datos

- **Decision**: PostgreSQL 16, acceso vía Spring Data JPA/Hibernate.
- **Rationale**: Estándar de facto para Spring Boot, soporta bien las
  transacciones y el modelo relacional simple de este dominio (Orden,
  Usuario, Registro de Ejecución). Testcontainers permite correr los tests de
  integración contra una instancia real en Docker, no contra un mock de BD.
- **Alternatives considered**: MySQL — equivalente, sin ventaja concreta para
  este dominio; se descarta por preferencia de ecosistema (extensiones JSONB
  de Postgres útiles si el resumen de IA necesita guardarse como estructura).
- **Revisar si**: se necesita una capacidad específica no soportada por
  Postgres (poco probable en este alcance).

## ADR-004: Almacenamiento de evidencia fotográfica

- **Decision**: Las fotos se guardan en un volumen de disco montado por
  Docker (no en la base de datos como BLOB); la tabla de Orden/Registro de
  Ejecución guarda solo la ruta/metadata (nombre, tipo, tamaño).
- **Rationale**: Mantiene la base de datos ligera y rápida de respaldar;
  evita el sobrecoste de leer/escribir binarios grandes vía JPA. Suficiente
  para el volumen de datos de un slice de curso (no se necesita un servicio
  de almacenamiento de objetos tipo S3).
- **Alternatives considered**: almacenamiento en BD como BLOB — descartado
  por rendimiento; servicio de almacenamiento de objetos externo (S3/MinIO)
  — descartado por añadir un componente de infraestructura no justificado
  para este alcance.
- **Revisar si**: el proyecto pasa de un contenedor único a despliegue
  multi-instancia (el volumen local ya no sería compartido entre instancias).

## Frontend: framework de testing

- **Decision**: Jasmine + Karma (scaffold estándar de Angular CLI), sin añadir
  Jest.
- **Rationale**: Cero configuración adicional; consistente con el Principio
  de no sobre-ingeniería del proyecto. Jest aporta velocidad pero no hay
  señal de que sea necesaria a este tamaño de suite de tests.

## Backend: testing

- **Decision**: JUnit 5 + Mockito para unit tests; Testcontainers (Postgres)
  para tests de integración y de contrato contra la API real.

## Seguridad — modelado de amenazas ligero (STRIDE) por grupo de endpoints

| Endpoint (grupo) | Amenaza relevante | Mitigación → AC de seguridad |
|---|---|---|
| Consulta de órdenes (GET) | Spoofing / Information Disclosure: un rol ve datos que no le corresponden | RBAC verificado en backend por rol (FR-002/003/004); 401/403 diferenciados (FR-017/018) |
| Registro de ejecución (POST) | Tampering: un technician registra ejecución sobre una orden ajena o en estado inválido | Validación de propiedad + estado en backend (FR-007), no solo en frontend |
| Aprobación/rechazo (POST) | Elevation of Privilege: un no-supervisor fuerza la petición directamente | `@PreAuthorize` por rol en el endpoint, verificado independientemente del frontend (FR-010) |
| Reasignación (POST) | Repudiation: nadie puede probar quién reasignó qué y cuándo | FR-023 (auditoría) cubre esto para rechazos de acceso; se extiende en data-model a registrar autor/fecha de cada reasignación |
| Resumen de IA (POST/GET) | Tampering: la nota se manipula entre el registro y el resumen; Denial of Service: llamadas repetidas a la API externa | Resumen se genera a partir de la nota ya persistida (no de input libre en la petición de resumen); ver ADR-001 para fallo/timeout |
| Todos | Cifrado en tránsito/reposo insuficiente | FR-019/FR-020 (HTTPS + cifrado en reposo) |

## Criterios de reversión

- **Backend/Frontend**: revertir al commit anterior vía git; al ser stateless
  (JWT) y sin migraciones destructivas previstas en este slice, un rollback de
  código no requiere pasos adicionales de datos.
- **Componente de IA**: si ADR-001 resulta problemático en producción (coste,
  disponibilidad), el fallback ya es parte del diseño (Principio V): apagar la
  llamada a la API y devolver siempre "evidencia insuficiente" no rompe el
  resto del flujo de aprobación/rechazo, que no depende del resumen para
  funcionar.
- **Migraciones de base de datos**: cada migración (Flyway) debe ser
  reversible o, si no lo es, documentarlo explícitamente antes de aplicarse en
  Tasks/Implement.

## T056: Cifrado en tránsito y en reposo (FR-019, FR-020, SC-006)

- **Decision (tránsito, FR-019)**: perfil Spring `prod` (`application-prod.yml`)
  con `server.ssl.enabled=true` sobre un keystore PKCS#12 autofirmado de
  desarrollo (`src/backend/generate-dev-cert.sh` documenta el comando
  `keytool` usado). Al no declararse un connector HTTP adicional, Tomcat solo
  expone HTTPS: toda conexión en texto plano falla en el handshake TLS, sin
  necesidad de un `TomcatConnectorCustomizer` adicional que rechace conexiones
  no-TLS. `docker-compose.yml` activa este perfil vía
  `SPRING_PROFILES_ACTIVE=prod` y expone `8443` en vez de `8080`.
- **Decision (tránsito hacia la BD, FR-020 parcial)**: la URL JDBC del perfil
  `prod` usa `sslmode=require`; el driver de PostgreSQL rechaza la conexión
  si el servidor no ofrece TLS. Para que esto funcione en `docker-compose`
  sin infraestructura externa, `docker/postgres/Dockerfile` construye una
  imagen de PostgreSQL 16 con `ssl=on` y un certificado autofirmado de
  desarrollo (`docker/postgres/generate-dev-cert.sh`, vía `openssl`), copiado
  y con permisos fijados (0600 la clave) durante el build de la imagen —no
  como bind mount, porque Postgres exige esos permisos exactos y los bind
  mounts en Docker Desktop/Windows no los preservan de forma fiable.
- **Decision (reposo, FR-020 — disco)**: el cifrado de disco real (at-rest
  encryption del volumen donde Postgres y el volumen de evidencia fotográfica
  persisten sus datos) se declara explícitamente **responsabilidad de la
  infraestructura del host/proveedor de nube**, no de la aplicación: no se
  añade una capa de cifrado a nivel de aplicación (p. ej. cifrar columnas o
  ficheros con una clave gestionada por el propio backend) porque el brief no
  la pide y sería sobre-ingeniería para este slice de curso — el patrón
  estándar en producción es delegarlo en el cifrado de volumen del proveedor
  (EBS/Persistent Disk cifrados, LUKS en el host, etc.), transparente para la
  aplicación.
- **Alternatives considered**: terminación TLS en un reverse proxy (nginx/
  Traefik) delante del backend — descartado por añadir un componente de
  infraestructura no justificado para un slice de curso cuando Spring Boot ya
  soporta TLS nativamente vía `server.ssl.*`; cifrado de aplicación sobre
  columnas sensibles (`pgcrypto` o cifrado en la capa JPA) — descartado por no
  estar pedido por el brief (que exige cifrado en tránsito y en reposo, no
  cifrado a nivel de columna) y por complejizar consultas/índices sin
  beneficio claro sobre el cifrado de volumen ya asumido a nivel de infra.
- **Revisar si**: el proyecto pasa de docker-compose local a un despliegue
  real — en ese caso, sustituir el certificado autofirmado por uno de una CA
  de confianza (o terminación TLS gestionada) y verificar que el proveedor de
  BD/volumen ofrece cifrado de disco por defecto (la mayoría de gestionados —
  RDS, Cloud SQL— lo hacen).

## Resueltas: Technical Context

Todas las entradas de Technical Context quedan resueltas con las decisiones
anteriores; no quedan marcadores `NEEDS CLARIFICATION` pendientes.
