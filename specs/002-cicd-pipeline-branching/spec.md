# Feature Specification: Pipeline de CI/CD y Estrategia de Ramas

**Feature Branch**: `002-cicd-pipeline-branching`

**Created**: 2026-07-13

**Status**: Draft

**Input**: User description: necesidad del equipo de ordenar cómo viaja el código
de FieldOps desde que se abre una rama de trabajo hasta que llega a producción,
con validaciones automáticas por componente, construcción y publicación de
imágenes, y despliegue controlado por entorno.

**Rige esta especificación**: `pipeline-constitution.md` (reglas no negociables
del pipeline, independiente de la constitution de la aplicación).

## Clarifications

### Session 2026-07-13

- Q: ¿Quién o qué determina la versión final que recibe una entrega en
  `main`? → A: El propio pipeline la calcula automáticamente a partir de los
  commits acumulados desde la última versión (no se crea un tag manual de
  antemano).
- Q: ¿"Autorizado para producción" significa cualquier mantenedor del
  repositorio, o una lista de revisores más acotada? → A: Una lista de
  revisores concreta, asignada específicamente al entorno de producción,
  distinta de (y más reducida que) el conjunto general de mantenedores.
- Q: ¿Existe una ruta de hotfix directa a `main` sin pasar por `develop`? →
  A: Sí. Esto amplía `pipeline-constitution.md` (v1.1.0) con un cuarto tipo
  de rama, `hotfix/*`, que llega a `main` sin pasar por `develop` pero sin
  saltarse ninguno de los gates bloqueantes, y que se reintegra en `develop`
  tras fusionarse.
- Q: La detección de componente por ruta de archivo (FR-006/FR-007) deja sin
  ningún gate a las PRs que solo tocan archivos de configuración del propio
  pipeline (workflows, `pipeline-constitution.md`, `pipeline-spec.md`) — justo
  el tipo de cambio que más necesita revisión. → A: Cualquier PR que toque
  esos archivos dispara siempre, como mínimo, la revisión del guardián de
  constitución, independientemente de si además toca un componente de la
  aplicación.
- Q: ¿Cuál es el tiempo objetivo para que el conjunto de validaciones de una
  pull request termine? → A: Menos de 15 minutos desde que se abre o
  actualiza la PR hasta que se conoce el resultado de todas sus validaciones.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bloquear una fusión con validaciones incompletas (Priority: P1)

Un desarrollador abre una pull request desde su rama de feature hacia
`develop`. Necesita que el sistema verifique automáticamente que su cambio
cumple todas las validaciones exigidas para el componente que tocó, y que no
pueda fusionarse si alguna falla — sin que dependa de que alguien se acuerde
de revisarlo a mano.

**Why this priority**: Es el punto de entrada de todo el pipeline. Si esto no
bloquea de verdad, el resto de garantías del sistema (versión limpia,
trazabilidad, no-rebuild) pierden sentido, porque cualquier cosa pudo colarse
en `develop` sin pasar por ellas.

**Independent Test**: Se abre una PR de prueba con un cambio que rompe
deliberadamente una de las validaciones (por ejemplo, un test que falla); se
verifica que la PR queda bloqueada para fusionar y que el motivo del bloqueo
es visible. Luego se corrige el cambio y se verifica que la PR pasa a poder
fusionarse.

**Acceptance Scenarios**:

1. **Given** una PR desde una rama de feature hacia `develop` que modifica el
   backend, **When** se ejecutan las validaciones, **Then** corren pruebas del
   backend, validación del contrato de la API, detección de cambios que
   rompen a consumidores existentes, búsqueda de secretos, verificación de
   criterios de aceptación contra la API real, análisis de vulnerabilidades
   de la imagen, y la revisión automática de cumplimiento de las reglas del
   sistema.
2. **Given** la misma PR pero modificando el frontend en vez del backend,
   **When** se ejecutan las validaciones, **Then** corren las que apliquen al
   frontend (pruebas, búsqueda de secretos, revisión de reglas del sistema),
   sin exigir las que son exclusivas del backend (contrato de API, breaking
   changes, imagen).
3. **Given** cualquiera de las validaciones anteriores falla, **When** se
   intenta fusionar la PR, **Then** el sistema lo impide y dice cuál validación
   falló.
4. **Given** todas las validaciones exigidas pasan, **When** se revisa el
   estado de la PR, **Then** queda marcada como lista para fusionar.

---

### User Story 2 - No disparar el pipeline del componente que no cambió (Priority: P1)

Un desarrollador cuyo cambio solo toca el frontend necesita que abrir su PR o
fusionarla no dispare ningún trabajo del backend (ni al revés), para no
esperar ni gastar recursos en validar o desplegar algo que no cambió.

**Why this priority**: Sin esto, cada cambio —por pequeño que sea— paga el
coste completo de ambos componentes, lo que en la práctica desincentiva
cambios pequeños y frecuentes, justo lo contrario de lo que se busca con este
pipeline.

**Independent Test**: Se abre una PR que solo modifica archivos dentro del
frontend; se verifica que únicamente arrancan los trabajos del frontend. Se
repite con una PR que solo toca el backend y se verifica lo simétrico.

**Acceptance Scenarios**:

1. **Given** una PR que solo modifica archivos del frontend, **When** se
   abre o actualiza, **Then** solo se ejecutan los trabajos asociados al
   frontend.
2. **Given** una PR que solo modifica archivos del backend, **When** se abre
   o actualiza, **Then** solo se ejecutan los trabajos asociados al backend.
3. **Given** una PR que modifica archivos de ambos componentes a la vez,
   **When** se abre o actualiza, **Then** se ejecutan los trabajos de ambos
   componentes.

---

### User Story 3 - Publicar una versión de prueba al integrar en develop (Priority: P2)

Un desarrollador cuyo cambio ya se fusionó en `develop` necesita que, de
forma automática, se genere una versión identificable de ese componente, se
publique en un sitio centralizado, y quede corriendo en el entorno de
desarrollo — sin tener que desplegarlo él mismo a mano.

**Why this priority**: Cierra el ciclo de "mi cambio ya está integrado" con
"mi cambio ya se puede ver corriendo", que es lo que de verdad permite probar
la integración entre cambios de distintas personas antes de que lleguen a
main.

**Independent Test**: Se fusiona un cambio de prueba en `develop`; se
verifica que aparece una imagen nueva en el registro con una versión
identificable ligada a ese commit, y que el entorno de desarrollo queda
corriendo esa versión sin intervención manual.

**Acceptance Scenarios**:

1. **Given** un cambio se acaba de fusionar en `develop`, **When** el
   pipeline correspondiente termina, **Then** existe una imagen publicada del
   componente que cambió, con una versión que identifica de forma única ese
   commit.
2. **Given** esa imagen ya está publicada, **When** se revisa el entorno de
   desarrollo, **Then** está corriendo esa misma versión, sin que nadie la
   haya desplegado a mano.
3. **Given** un cambio se fusiona en `develop` tocando solo un componente,
   **When** el pipeline corre, **Then** solo se construye, publica y despliega
   ese componente (coherente con la Historia 2).

---

### User Story 4 - Publicar una entrega formal al llegar a main (Priority: P2)

Un desarrollador cuyo cambio llega a `main` necesita que el sistema construya
una versión final identificable, la deje visible como una entrega formal
del proyecto (con lo necesario para poder recuperar exactamente ese build más
adelante), y la despliegue automáticamente en el entorno previo a producción.

**Why this priority**: Es el momento en que un conjunto de cambios pasa de
"integrado" a "candidato real a producción"; sin una entrega formal
identificable, no hay manera fiable de saber más adelante qué versión exacta
se probó en pre antes de decidir promoverla.

**Independent Test**: Se fusiona un cambio en `main`; se verifica que el
pipeline calcula por su cuenta la siguiente versión a partir de los commits
acumulados desde la última entrega, que se genera una entrega visible con
ese número, y que el entorno previo a producción queda corriendo esa
versión.

**Acceptance Scenarios**:

1. **Given** un cambio se fusiona en `main`, **When** el pipeline
   correspondiente termina, **Then** ha calculado por sí mismo la siguiente
   versión a partir de los commits acumulados desde la última entrega, y
   existe una entrega formal visible con ese número y con lo construido en
   ese momento adjunto.
2. **Given** esa entrega ya existe, **When** se revisa el entorno previo a
   producción, **Then** está corriendo esa misma versión, sin intervención
   manual.
3. **Given** los commits acumulados desde la última entrega no permiten
   determinar de forma inequívoca cuál es la siguiente versión, **When** se
   intenta ejecutar el pipeline, **Then** el sistema no inventa un número;
   señala explícitamente que no pudo calcularla.

---

### User Story 5 - Exigir autorización humana antes de tocar producción (Priority: P3)

Un miembro del equipo que forma parte de la lista de revisores de producción
necesita poder decidir, caso por caso, cuándo una versión ya probada en el
entorno previo pasa a producción — y necesita que el sistema nunca lo haga
por su cuenta sin esa decisión.

**Why this priority**: Es la última barrera antes de un cambio que afecta a
usuarios reales; el resto del pipeline puede automatizarse sin riesgo
porque esta historia garantiza que ese paso concreto no lo esté.

**Independent Test**: Una versión llega al entorno previo a producción; se
verifica que el despliegue a producción no ocurre hasta que alguien de la
lista de revisores de ese entorno lo autoriza explícitamente, y que sí ocurre
justo después de esa autorización.

**Acceptance Scenarios**:

1. **Given** una versión ya desplegada en el entorno previo a producción,
   **When** nadie de la lista de revisores de producción ha autorizado el
   paso a producción, **Then** el sistema no despliega nada ahí.
2. **Given** esa misma versión, **When** alguien de esa lista de revisores la
   autoriza explícitamente, **Then** el sistema la despliega en producción
   usando la misma imagen ya publicada, sin reconstruirla.
3. **Given** una persona con acceso de mantenedor al repositorio pero que NO
   forma parte de la lista de revisores asignada a producción, **When**
   intenta autorizar el paso a producción, **Then** el sistema no se lo
   permite — pertenecer al repositorio no basta, hay que estar en esa lista
   concreta.

---

### User Story 6 - Corregir producción sin esperar a que develop esté listo (Priority: P3)

Alguien del equipo necesita poder llevar una corrección urgente hasta
producción sin depender de que todo lo demás acumulado en `develop` esté ya
en condiciones de desplegarse, pero sin que esa urgencia sea excusa para
saltarse ninguna validación.

**Why this priority**: Es la excepción al camino normal (feature → develop →
main); existe para el caso realista en que develop no está en un estado
desplegable pero producción necesita una corrección ya. Es de prioridad baja
porque no forma parte del camino habitual, pero sin ella el equipo no tendría
ninguna vía legítima para ese escenario.

**Independent Test**: Se crea una rama de corrección directamente desde
`main` y se fusiona de vuelta a `main` sin pasar por `develop`; se verifica
que pasa exactamente las mismas validaciones que cualquier otra fusión a
`main`, y que el cambio queda también reflejado en `develop` después.

**Acceptance Scenarios**:

1. **Given** una rama de corrección creada directamente desde `main`,
   **When** se abre como pull request hacia `main`, **Then** se le exigen las
   mismas validaciones bloqueantes que a cualquier pull request hacia
   `develop` para el componente que toca.
2. **Given** esa corrección se fusiona en `main`, **When** el pipeline
   termina, **Then** se comporta igual que cualquier otra fusión a `main`
   (versión calculada, entrega formal, despliegue a pre) y además el cambio
   se reintegra en `develop` para que no se pierda en la siguiente entrega
   normal.
3. **Given** cualquier otra rama que no sea de este tipo dedicado a
   correcciones urgentes, **When** intenta fusionarse directamente a `main`,
   **Then** el sistema lo rechaza — la única excepción al camino
   feature→develop→main es esta.

---

### Edge Cases

- ¿Qué ocurre si una PR modifica archivos que no pertenecen claramente a
  ningún componente (por ejemplo, solo documentación en la raíz del repo)?
  No debería disparar ningún pipeline de validación de componente.
- ¿Qué ocurre si dos PRs a `develop` se fusionan casi a la vez? Cada una
  genera su propia versión de prueba identificada por su propio commit, sin
  que una sobrescriba a la otra.
- ¿Qué ocurre si la revisión automática de cumplimiento de las reglas del
  sistema encuentra una violación? Se trata igual que cualquier otra
  validación fallida: bloquea la fusión.
- ¿Qué ocurre si una corrección urgente (User Story 6) y una entrega normal
  desde `develop` intentan calcular la siguiente versión casi al mismo
  tiempo? El cálculo se basa en lo que ya existe publicado en el momento en
  que cada una termina, no en un número reservado de antemano.
- ¿Qué ocurre si la pull request de reintegración de un hotfix (FR-019)
  falla alguna de sus propias validaciones? El despliegue a producción que
  ya ocurrió con el hotfix se mantiene sin cambios — no hay una reversión
  automática. Solo queda bloqueada la reintegración hacia `develop`, que
  permanece pendiente hasta que se corrija y esa pull request pase sus
  gates.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE ejecutar, para toda pull request desde una rama
  de feature hacia `develop`, el conjunto de validaciones correspondiente al
  componente (backend, frontend, o ambos) que esa PR modifica.
- **FR-002**: El conjunto de validaciones del backend DEBE incluir, como
  mínimo: pruebas automáticas del código, validación del contrato de la API,
  detección de cambios que rompan a consumidores existentes de esa API,
  búsqueda de secretos filtrados, verificación de que los criterios de
  aceptación siguen cumpliéndose contra la API real, y análisis de
  vulnerabilidades de la imagen que se construiría. El umbral de severidad
  que hace fallar esta última comprobación (por ejemplo, solo CRITICAL/HIGH,
  o cualquier hallazgo) es una decisión de configuración de la herramienta
  de escaneo, no de este requisito — ver `research.md`.
- **FR-003**: El conjunto de validaciones del frontend DEBE incluir, como
  mínimo: pruebas automáticas del código y búsqueda de secretos filtrados.
- **FR-004**: Toda pull request, sin importar el componente, DEBE pasar
  además una revisión automática de que el cambio no viola las reglas no
  negociables del sistema.
- **FR-005**: El sistema DEBE impedir la fusión de una pull request mientras
  cualquiera de sus validaciones exigidas no haya pasado.
- **FR-006**: El sistema DEBE identificar el componente afectado por una
  pull request o una fusión a partir de qué archivos cambian, sin depender de
  que la persona lo declare manualmente.
- **FR-007**: Una pull request o fusión que no modifique archivos de ningún
  componente NO DEBE disparar las validaciones ni los pipelines de
  construcción/despliegue de ninguno de los dos.
- **FR-008**: Al fusionarse un cambio en `develop`, el sistema DEBE construir
  y publicar automáticamente una imagen del componente afectado, identificada
  de forma única por el commit que la origina.
- **FR-009**: Al fusionarse un cambio en `develop`, el sistema DEBE desplegar
  automáticamente la imagen recién publicada en el entorno de desarrollo,
  sin desplegarla en ningún otro entorno.
- **FR-010**: Al fusionarse un cambio en `main`, el sistema DEBE calcular por
  sí mismo la siguiente versión del componente afectado a partir de los
  commits acumulados desde la última entrega, y construir y publicar
  automáticamente una imagen identificada con esa versión.
- **FR-011**: Si ninguno de los commits acumulados desde la última entrega
  coincide con un prefijo de Conventional Commits reconocido por el sistema
  (por ejemplo `feat:`, `fix:`, o un pie `BREAKING CHANGE:`), el sistema NO
  DEBE inventar un número de versión; DEBE señalar explícitamente que no
  pudo calcularla. La lista exacta de prefijos reconocidos es un detalle de
  configuración de la herramienta de versionado (ver `research.md`,
  ADR-P2), no de este requisito.
- **FR-012**: Al fusionarse un cambio en `main`, el sistema DEBE publicar una
  entrega formal visible (con lo construido en ese momento adjunto) asociada
  a esa versión.
- **FR-013**: Al fusionarse un cambio en `main`, el sistema DEBE desplegar
  automáticamente la imagen recién publicada en el entorno previo a
  producción.
- **FR-014**: El sistema NO DEBE desplegar en el entorno de producción sin
  una autorización explícita de alguien perteneciente a la lista de
  revisores asignada específicamente a ese entorno.
- **FR-015**: El sistema DEBE rechazar intentos de autorizar el despliegue a
  producción por parte de cualquier persona que no forme parte de esa lista
  de revisores, tenga o no acceso de mantenedor al repositorio.
- **FR-016**: Todo despliegue, en cualquier entorno, DEBE usar la imagen ya
  construida y publicada previamente; el sistema NO DEBE reconstruir la
  aplicación como parte de un despliegue.
- **FR-017**: El sistema DEBE permitir que una rama dedicada a correcciones
  urgentes (`hotfix/*`) llegue a `main` sin pasar por `develop`, exigiéndole
  exactamente las mismas validaciones bloqueantes que a cualquier otra
  fusión a `main` para el componente que toca.
- **FR-018**: El sistema DEBE rechazar cualquier fusión directa a `main` que
  no venga de `develop` ni de una rama `hotfix/*`.
- **FR-019**: Cuando una rama `hotfix/*` se fusiona en `main`, el sistema
  DEBE abrir automáticamente una pull request de reintegración hacia
  `develop` con ese mismo cambio. El cambio no se considera completamente
  "reflejado en develop" hasta que esa pull request de reintegración pasa
  sus propias validaciones y se fusiona — abrirla no basta por sí sola.
- **FR-020**: Toda pull request que modifique archivos de configuración del
  propio pipeline (workflows, `pipeline-constitution.md`,
  `pipeline-spec.md`) DEBE disparar, como mínimo, la revisión automática de
  cumplimiento de las reglas del sistema (FR-004), sin importar si la
  detección de componente por ruta de archivo (FR-006) identifica o no un
  componente de la aplicación en esa misma PR.

### Key Entities

- **Componente**: unidad desplegable independiente (backend o frontend); cada
  uno tiene su propio conjunto de validaciones, su propia versión y su propio
  ciclo de construcción/despliegue.
- **Validación (Gate)**: comprobación automática asociada a un componente y a
  un momento del ciclo (pull request); tiene un resultado binario (pasa/no
  pasa) que determina si una fusión puede continuar.
- **Versión publicada**: identificador único de una construcción de un
  componente (versión de prueba ligada a un commit en `develop`, o versión
  final ligada a un tag en `main`), asociado a una imagen concreta en el
  registro.
- **Entorno**: destino de despliegue (desarrollo, previo a producción,
  producción) con su propia condición de disparo y, en el caso de
  producción, su propia exigencia de autorización.
- **Pull Request**: solicitud de fusión con una rama de origen y una rama de
  destino; su rama de origen determina qué validaciones se le exigen (una
  de feature hacia `develop`, una `hotfix/*` hacia `main`) y si cuenta como
  la excepción admitida a la regla general de que `main` solo recibe
  fusiones desde `develop`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las pull requests con al menos una validación
  fallida quedan bloqueadas para fusionar hasta que se corrigen.
- **SC-002**: El 100% de las pull requests o fusiones que solo tocan un
  componente ejecutan únicamente los trabajos de ese componente, sin
  disparar ningún trabajo del otro.
- **SC-003**: El 100% de las fusiones a `develop` producen una versión de
  prueba publicada y desplegada en desarrollo en menos de 10 minutos desde
  que se completa la fusión.
- **SC-004**: El 100% de las fusiones a `main` (por develop o por hotfix) con
  una versión calculable producen una entrega formal visible y un despliegue
  en el entorno previo a producción, sin intervención manual.
- **SC-005**: El 100% de los despliegues a producción registran quién los
  autorizó y cuándo, y ninguno ocurre sin la autorización de alguien de la
  lista de revisores de ese entorno.
- **SC-006**: El 100% de los despliegues, en cualquier entorno, corresponden
  exactamente a una imagen que ya había sido validada y publicada antes del
  despliegue (verificable comparando el identificador de la imagen
  desplegada con el de la imagen publicada por el pipeline de origen).
- **SC-007**: El 100% de las fusiones directas a `main` que no vienen de
  `develop` ni de una rama `hotfix/*` son rechazadas.
- **SC-008**: El conjunto de validaciones de una pull request termina en
  menos de 15 minutos desde que se abre o actualiza, en al menos el 95% de
  los casos. Esta ventana se mide en tiempo de reloj real desde la apertura
  o actualización hasta el resultado final visible en la PR — incluye
  cualquier tiempo de cola del runner, no solo el tiempo de ejecución activa
  de los jobs.

## Assumptions

- Se asume que cada pull request declara con claridad, mediante las rutas de
  archivo que modifica, a qué componente pertenece; no se contempla un
  mecanismo de override manual para forzar qué validaciones corren.
- No se cubre en esta especificación qué ocurre si una validación falla por
  una causa externa e intermitente (por ejemplo, el propio servicio de
  escaneo de vulnerabilidades no responde); se asume que un fallo de
  cualquier validación, sea cual sea la causa, bloquea la fusión igual.
- Se asume que el equipo sigue Conventional Commits de forma consistente en
  las ramas que llegan a `develop`/`main`; el cálculo automático de versión
  (FR-010/FR-011) depende enteramente de que esa convención se respete y no
  se contempla en esta especificación un mecanismo de corrección retroactiva
  si un commit histórico no la sigue.
