<!--
Sync Impact Report
==================
Version change: 1.0.0 → 1.2.0 (1.1.0: overlays del flujo SDD; 1.2.0: mapa de
  agentes/skills de referencia)
Modified principles:
  - I. Spec-Antes-Que-Código: se añaden dos revisiones internas de calidad de
    la spec (limpieza de vaguedad tras el borrador; búsqueda de huecos
    estructurales tras el checklist) y se explicita que el gate de
    constitution deja de ser un documento de lectura para convertirse en una
    comprobación que puede bloquear el avance.
  - III. Trazabilidad y Verificabilidad Total: se añade que la trazabilidad
    nace en Specify (no se documenta al final) y que las suposiciones no
    resolubles solo con el brief se registran aparte, con su impacto.
  - V. IA Sin Alucinación: se explicita que el conjunto de golden cases se
    define durante Plan (antes de construir el componente) y se ejecuta
    después como comprobación bloqueante, no como referencia opcional.
Added principles:
  - VIII. Riesgo Técnico y de Seguridad Explícito en el Plan (ADRs con
    caducidad, modelado de amenazas por endpoint, criterios de reversión)
Added sections:
  - Agentes de Referencia (mapa de qué agente/skill de .claude/agents y
    .claude/skills usar para cada tecnología o tipo de documentación)
Removed sections: ninguna
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ compatible sin cambios (su
    "Constitution Check" sigue leyendo este archivo dinámicamente)
  - .specify/templates/spec-template.md ✅ compatible sin cambios
  - .specify/templates/tasks-template.md ✅ compatible sin cambios
  - README.md ⚠ pendiente: se crea durante /speckit-implement
Follow-up TODOs:
  - TODO(CI): las comprobaciones encadenadas descritas en Implement
    (trazabilidad → evals → revisión adversarial → constitution) hoy se
    ejecutan manualmente; formalizarlas como pipeline de CI queda para cuando
    exista un repositorio remoto con Actions (o equivalente) configurado.
  - TODO(README): crear README.md con pasos de arranque (Docker) y
    verificación (tests) en cuanto exista código ejecutable.
-->

# FieldOps Constitution

## Core Principles

### I. Spec-Antes-Que-Código (NON-NEGOTIABLE)

No se escribe código de producción, contrato de API ni test hasta que el
artefacto de spec correspondiente exista y haya sido aprobado. Las fases se
recorren en el orden fijo descrito en Development Workflow, y cada una
termina en un commit independiente, de modo que el historial de git por sí
solo demuestre qué se especificó antes de implementarse. Pasar de fase
requiere una confirmación explícita del autor; hacerlo sin ella se trata como
una violación de este principio, no como una optimización de tiempo.

Dentro de la propia fase de spec hay dos momentos de revisión que no se
saltan por prisa: al cerrar el primer borrador, se relee buscando frases que
no comprometen a nada verificable (adjetivos sin métrica, "debería funcionar
bien", condiciones sin número); y al cerrar el checklist, antes de pasar a
plan, se busca específicamente lo que la primera lectura no detecta —un caso
de uso completo que falta, una combinación de roles no contemplada— porque
ese tipo de hueco requiere mirar la spec como un todo, no frase a frase.

Además, esta constitution no es un documento que se lee una vez y se archiva:
cada fase posterior (en particular Analyze e Implement) la usa como lista de
comprobación activa, y una violación detectada bloquea el avance igual que un
test que falla.

**Rationale**: El objetivo del proyecto es demostrar el dominio del proceso,
no solo producir código que funcione. Una decisión de negocio que se deja sin
resolver en la spec no desaparece: reaparece más tarde, cuando ya hay código
de frontend y backend construido sobre el supuesto equivocado, y ahí cuesta
mucho más corregirla. Separar la revisión de redacción (superficial) de la
revisión de huecos (estructural) evita gastar la segunda en corregir adjetivos.

### II. RBAC en Doble Capa (NON-NEGOTIABLE)

Cada acción del sistema pertenece a exactamente un rol (dispatcher,
technician o supervisor), y quien decide si esa acción se ejecuta es siempre
el backend, no la interfaz. Angular puede decidir qué mostrar según el rol de
sesión, pero eso solo cambia lo que el usuario ve; no cambia lo que la API
acepta. Cualquier petición HTTP que llegue directamente al servidor —venga o
no de la pantalla que Angular renderiza— pasa por la misma comprobación de
rol, y esa comprobación devuelve **401** si no hay sesión válida o **403** si
la sesión es válida pero pertenece a un rol distinto del requerido.

**Rationale**: FieldOps maneja datos de clientes finales a través de tres
roles con permisos distintos; un control de acceso que solo existe en la capa
visual es el tipo de fallo de seguridad más fácil de introducir sin darse
cuenta y más caro de descubrir tarde.

### III. Trazabilidad y Verificabilidad Total

Cada criterio de aceptación definido en la spec debe quedar enlazado a un
test concreto que lo ejercite, y esa relación se mantiene visible en
`docs/traceability.md`. Si un criterio no tiene un test que lo respalde, se
considera pendiente aunque el comportamiento observado a simple vista parezca
correcto: la verificación se basa en leer el test real, nunca en inferir
cobertura por parecido de nombres o por confianza en que "seguro que ya está".

Esta trazabilidad no se reconstruye al final a modo de justificación: nace en
cuanto existen los primeros FRs y criterios de aceptación (durante Specify), y
se va completando en cada fase siguiente hasta usarse como base de cobertura
en Analyze y en la revisión de cierre. De forma paralela, cualquier cosa que
el autor no pueda decidir solo leyendo el brief —y que por tanto resuelva
asumiendo algo— se registra en `docs/assumptions.md` junto con qué pasaría si
esa suposición resultase equivocada, en vez de quedar enterrada en la cabeza
de quien la tomó.

**Rationale**: Un slice pequeño y bien acotado solo aporta valor si se puede
demostrar que cumple lo que promete; sin esa trazabilidad, "está terminado" es
una afirmación de fe, no un hecho comprobable. Y una suposición no registrada
es una decisión de facto que nadie más puede auditar ni cuestionar.

### IV. Contrato Antes de Implementar

El contrato de la API (`contracts/openapi.yaml`) se redacta a partir de la
spec antes de tocar el controller del backend o el servicio que lo consume en
el frontend, incluyendo los esquemas de request/response y los códigos de
error relevantes por endpoint (401/403 incluidos). Una vez existe
implementación, los tests de contrato comprueban que no se ha desviado de lo
publicado.

**Rationale**: Cuando frontend y backend se construyen cada uno contra su
propia idea de cómo debería ser la API, la incompatibilidad se descubre en
integración, que es el momento más caro de arreglarla. El contrato compartido
elimina esa ambigüedad desde el principio.

### V. IA Sin Alucinación (Fail-Safe por Defecto)

El asistente que resume la incidencia de una orden a partir de las notas del
técnico debe reconocer cuándo esas notas no alcanzan para construir un
resumen fiable, y en ese caso responder indicándolo en vez de rellenar el
hueco inventando contenido. Este comportamiento se comprueba con un conjunto
de golden cases y un umbral mínimo de aciertos (`/evals`). Un resumen
inventado sobre una nota vacía o insuficiente se trata como un fallo grave del
componente, aunque el resto de casos pase el umbral general.

Ese conjunto de golden cases y su umbral se decide durante Plan, antes de que
exista una sola línea del componente — no se redacta después para justificar
lo que ya salió del desarrollo. Una vez el componente existe, ejecutar esas
evals deja de ser una referencia consultiva y pasa a ser una comprobación que
puede bloquear el cierre de la tarea, igual que cualquier otro test.

**Rationale**: El supervisor usa ese resumen para decidir si valida o no el
trabajo del técnico; un resumen que suena bien pero no está respaldado por la
nota original induce a esa decisión a error de forma más silenciosa que
simplemente no tener resumen. Definir primero "qué cuenta como acierto" evita
mover la meta una vez visto el resultado.

### VI. Clean Code y Buenas Prácticas Spring Boot + Angular

El backend sigue las convenciones propias de Spring Boot (inyección por
constructor, DTOs que no exponen las entidades JPA directamente, capas
controller/service/repository separadas, transacciones acotadas a lo
necesario) y el frontend las de Angular (componentes con una responsabilidad
clara, formularios reactivos, suscripciones RxJS que se limpian
correctamente, tipado estricto evitando `any` sin justificar). En ambos casos
se exige nombres que expliquen su propósito, funciones y componentes cortos,
y ausencia de duplicación evidente.

**Rationale**: La consigna del proyecto es entregar un slice pequeño pero bien
hecho; la calidad del código es parte de lo que distingue "bien hecho" de
"funciona de milagro".

### VII. Entorno Reproducible y Tests en Verde

Todo el sistema se levanta mediante Docker/Docker Compose, y el `README.md`
recoge los comandos exactos para instalarlo y para ejecutar la suite de tests,
de forma que cualquier persona ajena al desarrollo pueda repetir esos pasos
sin depender de configuración previa en su equipo. Una funcionalidad se
considera pendiente mientras cualquiera de sus tres piezas —el endpoint que la
sirve, la pantalla que la consume, o el test que la comprueba— falte o esté
rota; construir una sin las otras dos no cuenta como progreso entregable.

**Rationale**: Un proyecto que solo se puede verificar en el ordenador de
quien lo escribió no es realmente verificable por nadie más — y eso vacía de
sentido el Principio III a nivel de todo el proyecto, no solo de una feature.

### VIII. Riesgo Técnico y de Seguridad Explícito en el Plan

Toda decisión técnica tomada durante Plan que no sea la única opción obvia
—elegir una estrategia de concurrencia, una forma de acceso a datos, cómo
estructurar un módulo— se registra como decisión de diseño: qué se eligió, qué
alternativa se descartó y por qué, y bajo qué circunstancia futura habría que
volver a mirarla (por ejemplo, si cambia el volumen de órdenes o se añade un
rol nuevo). Esa decisión no se archiva sin fecha de revisión implícita.

Plan es también el momento de pensar, endpoint por endpoint, qué puede salir
mal desde el punto de vista de seguridad (alguien suplanta a otro rol, alguien
lee datos que no le corresponden, alguien repite una petición para forzar un
estado inválido) y convertir cada amenaza relevante en un criterio de
aceptación de seguridad verificable, no en una preocupación que se queda solo
mencionada. Por último, para cada pieza que se vaya a entregar se deja escrito
cómo se revertiría si algo falla después de integrarla —revertir el commit,
desactivar una ruta, apagar el componente de IA y devolver un resumen vacío—
aunque este proyecto no tenga detrás un despliegue productivo real.

**Rationale**: La tecnología concreta entra en juego precisamente en Plan; es
el punto donde resolver estas preguntas cuesta menos que descubrirlas en
Implement o, peor, después de que el supervisor haya aprobado algo basado en
un fallo de seguridad no detectado.

## Additional Constraints

**Stack tecnológico**: backend en Spring Boot (Java) con SQL vía JPA/Hibernate;
frontend en Angular con TypeScript y Tailwind CSS; el componente de IA se
trata como un servicio con contrato propio (entrada: notas del técnico;
salida: resumen o aviso explícito de evidencia insuficiente). Todo se ejecuta
sobre contenedores Docker.

**Organización del repositorio**: cada tipo de artefacto tiene una única
ubicación reconocida, para que cualquiera (incluido un evaluador externo)
sepa dónde buscar sin tener que preguntar: la documentación del proceso
(spec, plan, tareas, etc.) separada del código; el contrato de la API
separado de su implementación; la matriz de trazabilidad y el registro de
suposiciones separados de los tests y decisiones que documentan; y los golden
cases del componente de IA separados del propio componente, para que puedan
ejecutarse de forma independiente. El `README.md` de la raíz es siempre el
punto de entrada para instalar y verificar el proyecto completo.

**Roles del dominio**: dispatcher, technician y supervisor son los únicos
roles reconocidos por el sistema; cualquier intento de ejecutar una acción
fuera del rol al que pertenece se rechaza explícitamente en el backend.

## Development Workflow

El trabajo avanza por gates secuenciales, no por fases que se puedan saltar o
adelantar porque "total, ya sé lo que hay que hacer". Cada gate produce un
artefacto concreto y revisable, y ese artefacto es el único criterio válido
para decidir si se puede avanzar al siguiente. El autor da su aprobación gate
a gate; sin ella, el trabajo posterior no arranca, sin importar cuánta prisa
haya.

El recorrido completo, en el orden en que ocurre:

1. **Constitution** — se fijan los principios no negociables (este documento).
2. **Specify** — se traduce el brief a requisitos verificables; al cerrar el
   primer borrador se limpia la vaguedad superficial (ver Principio I).
3. **Clarify** — se resuelven las ambigüedades detectadas y se registran las
   suposiciones no resolubles solo con el brief en `docs/assumptions.md`.
4. **Checklist** — se comprueba que la spec está completa antes de planificar.
5. **Revisión estructural** — entre checklist y plan, se buscan huecos que la
   revisión de redacción no detecta (casos de uso incompletos, interacciones
   entre roles sin cubrir).
6. **Plan** — entra la tecnología concreta: diseño técnico, decisiones
   registradas, modelado de amenazas y criterios de reversión (Principio
   VIII), y definición del conjunto de evals de IA (Principio V).
7. **Validación del plan** — antes de descomponer en tareas, se comprueba que
   plan, contrato y spec son coherentes entre sí; es al plan lo que el
   checklist es a la spec.
8. **Tasks** — se descompone el trabajo en tareas ejecutables y verificables.
9. **Analyze** — se audita la consistencia entre todos los artefactos
   anteriores y la cobertura de `docs/traceability.md`; una violación de un
   Core Principle en este punto se trata como crítica, no como nota menor.
10. **Implement** — se construyen frontend, backend y tests a la vez. Antes de
    dar por cerrado el trabajo se repasan, en cascada, la trazabilidad, las
    evals de IA, una segunda pasada de revisión de huecos, y el cumplimiento
    de esta constitution; cualquier fallo en esa cadena bloquea el cierre.
11. **Revisión de cierre** — al terminar, se responde por escrito a cuatro
    preguntas antes de considerar el slice entregado: ¿funciona de verdad?,
    ¿se entiende sin tener que preguntar al autor?, ¿los tests realmente
    fallarían si se rompiera lo que dicen proteger?, ¿el autor lo firmaría tal
    cual está? Esta revisión queda documentada en `docs/SLICE-REVIEW.md`.

Cada uno de estos puntos se cierra con su propio commit, de modo que el orden
real en que se trabajó quede grabado en el historial y no dependa de la
memoria de nadie. Como red de seguridad adicional, el hook `spec-gate`
(`.claude/hooks/spec-gate.sh`) bloquea a nivel de sistema de archivos
cualquier escritura bajo `src/`, `tests/` o `contracts/` mientras no existan
todos los artefactos de spec previos — es una comprobación mecánica de que los
archivos están ahí, no un sustituto de la revisión y aprobación humana en cada
gate.

## Agentes de Referencia

Cada tramo del trabajo tiene un agente especializado asignado (definidos en
`.claude/agents/`); usar el agente correcto para cada tarea, en vez de
cualquiera que esté a mano, es parte de cómo este proyecto respeta los
principios anteriores:

- **Backend (Spring Boot + SQL)**: implementar con `java-expert`; toda
  implementación pasa después por `java-reviewer` antes de darse por cerrada
  (Principios II, VI).
- **Frontend (Angular + TypeScript + Tailwind)**: implementar con
  `frontend-expert`; revisar con `frontend-reviewer` antes de cerrar
  (Principios II, VI).
- **Diseño de pantallas/flujos de usuario**: `design-expert`, incluyendo
  mockups navegables cuando una propuesta necesite validarse antes de
  implementarse.
- **Documentación técnica** (README, contrato de API, ADRs, notas de
  arquitectura): `docs-writer`, siempre a partir del código y artefactos ya
  existentes, nunca de lo que "se supone que debería hacer".
- **Documentación funcional** (historias de usuario, reglas de negocio,
  flujos por rol): `functional-writer`, en el lenguaje del negocio, sin
  detalles de implementación.

Además, tres skills (`.claude/skills/`) cubren los artefactos que exige esta
constitution y no corresponden a un solo agente: `traceability-matrix`
mantiene `docs/traceability.md` (Principio III), `api-contract` mantiene
`contracts/openapi.yaml` (Principio IV), y `ai-eval-harness` mantiene
`/evals` (Principio V).

## Governance

Esta constitution tiene prioridad sobre cualquier práctica ad-hoc que surja
durante el desarrollo. Al ser un proyecto de autoría individual, las
enmiendas las decide y aprueba el propio autor; aun así, cada enmienda debe
quedar documentada con su Sync Impact Report correspondiente para que el
historial de cambios sea auditable.

**Versionado** (SemVer aplicado a esta constitution):
- MAJOR: se elimina o redefine un principio existente de forma incompatible.
- MINOR: se añade un principio nuevo o se amplía sustancialmente una guía.
- PATCH: aclaraciones, correcciones de redacción o cambios no semánticos.

**Revisión de cumplimiento**: en particular durante Analyze e Implement, se
verifica explícitamente que los artefactos y el código no contradicen ninguno
de los principios anteriores. Cualquier desviación detectada se documenta y se
resuelve antes de seguir adelante; no se acepta una excepción sin dejar
constancia escrita de por qué era necesaria.

**Version**: 1.2.0 | **Ratified**: 2026-07-10 | **Last Amended**: 2026-07-10
