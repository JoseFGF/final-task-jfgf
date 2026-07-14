# Assumptions

Registro de lo que no se pudo decidir solo con el brief de FieldOps, según el
Principio III de la constitution. Cada fila documenta la pregunta que el brief
deja abierta, la suposición tomada para poder avanzar, y qué pasaría si esa
suposición resultase equivocada.

| Pregunta | Suposición | Impacto si es incorrecta |
|----------|------------|---------------------------|
| ~~¿Quién crea una orden y la mueve de `draft` a `assigned`?~~ **Resuelto por `003-order-management-enhancements`**: el dispatcher crea la orden (`POST /orders`, FR-007) y la asigna explícitamente (`POST /orders/{id}/reassignment` sobre `draft`, FR-004/FR-005) o ya en la propia creación (FR-007b). | (Ya no es una suposición abierta — ver `specs/003-order-management-enhancements/spec.md`.) | — |
| ¿Puede un mismo usuario operar con más de un rol a la vez (por ejemplo, ser dispatcher y technician simultáneamente)? | Se asume un modelo de sesión estándar: un usuario autenticado tiene un único rol fijo por sesión. | Si se necesitan roles múltiples simultáneos, el modelo de RBAC (Principio II) y las comprobaciones 401/403 tendrían que rediseñarse para resolver conflictos de permisos entre roles. |
| ¿Qué formato y límites debe cumplir la foto de evidencia? El brief solo exige "al menos una foto". | Se asume formatos de imagen estándar (JPEG/PNG) sin límite explícito de tamaño o cantidad más allá de lo razonable para una app móvil de campo. | Si el negocio necesita límites concretos (tamaño máximo, número máximo de fotos, formatos adicionales como PDF), el contrato de la API (Principio IV) y la validación en el registro de ejecución (US2) tendrían que actualizarse. |
| ¿FieldOps sirve a una única organización o a varios clientes con datos aislados entre sí? | Se asume un único tenant: no hay mención en el brief de necesidad de separar datos entre distintas empresas clientes. | Si se requiere multi-tenancy, el modelo de datos y el RBAC (Principio II) necesitarían una dimensión adicional de aislamiento por organización, afectando prácticamente todos los FRs. |
