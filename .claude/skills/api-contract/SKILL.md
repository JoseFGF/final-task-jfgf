---
name: api-contract
description: Genera y mantiene contracts/openapi.yaml, el contrato de la API antes de implementar. Úsalo cuando el usuario pida "genera el contrato de la API", "actualiza el openapi", o durante /speckit.plan o /speckit.implement cuando haga falta definir el contrato antes de tocar código backend/frontend. No implementa endpoints ni UI, solo el contrato.
---

# API Contract

Esta skill define/mantiene el contrato de la API como fuente de verdad, antes de que exista implementación. No escribe código de backend ni frontend — eso lo hacen java-expert y frontend-expert a partir de este contrato.

## Pasos

1. **Localizar la spec**: lee `.specify/spec.md` (o `/specs/spec.md`) para identificar qué operaciones necesita la API: FRs, roles involucrados (RBAC) y qué rol puede ejecutar cada acción.

2. **Generar/actualizar `contracts/openapi.yaml`** (OpenAPI 3.x) con, por cada endpoint:
   - Método, path, descripción ligada al FR que lo origina
   - Request schema (body/params/query) con tipos explícitos, nada de `object` genérico si el dato tiene forma conocida
   - Response schema para el caso de éxito
   - Códigos de error explícitos y diferenciados:
     - **401** — no autenticado
     - **403** — autenticado pero sin el rol/permiso requerido para esa acción
     - Otros códigos relevantes según la spec (404, 409, 422, etc.)
   - Roles permitidos documentados (en `description` o extensión `x-roles` si el proyecto no tiene convención propia)

3. **Si ya existe implementación**: contrasta el contrato contra los endpoints reales del backend (controllers) y señala discrepancias explícitamente — endpoint implementado sin contrato, o contrato sin endpoint implementado. No asumas que están sincronizados; verifícalo leyendo el código.

4. **Reporta** un resumen: endpoints definidos, cuáles ya tienen implementación, cuáles no, y cualquier discrepancia encontrada.

## Reglas

- El contrato es la fuente de verdad si aún no hay implementación: escribe primero contra lo que dice la spec, no contra lo que "sería razonable".
- Nunca omitas 401/403 en un endpoint que requiera autenticación o rol específico — es un requisito técnico mínimo del proyecto (RBAC en doble capa).
- No implementes el código del endpoint ni del cliente; si hace falta, indica que corresponde a java-expert o frontend-expert.
- Si la spec no deja claro el rol permitido para una acción, señálalo como ambigüedad en vez de decidir tú mismo.
