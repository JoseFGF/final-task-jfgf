# Pipeline Spec — FieldOps CI/CD

Este archivo existe para que el nombre literal que pide el enunciado del
Módulo 12 (`pipeline-spec.md`) esté presente en el repositorio. El contenido
real —FRs en EARS, NFRs, ACs, clarificaciones y criterios de éxito del
pipeline— vive en la especificación de la feature, siguiendo la misma
convención speckit que el resto del proyecto (`001-*`, `003-*`, `004-*`):

- **Spec**: [`specs/002-cicd-pipeline-branching/spec.md`](specs/002-cicd-pipeline-branching/spec.md)
- **Plan**: [`specs/002-cicd-pipeline-branching/plan.md`](specs/002-cicd-pipeline-branching/plan.md)
- **Research / ADRs**: [`specs/002-cicd-pipeline-branching/research.md`](specs/002-cicd-pipeline-branching/research.md)
- **Tareas**: [`specs/002-cicd-pipeline-branching/tasks.md`](specs/002-cicd-pipeline-branching/tasks.md)
- **Contrato de status checks**: [`specs/002-cicd-pipeline-branching/contracts/required-status-checks.md`](specs/002-cicd-pipeline-branching/contracts/required-status-checks.md)

Las reglas no negociables del pipeline (independientes de este spec) están en
[`.specify/pipeline-constitution.md`](.specify/pipeline-constitution.md).

No dupliques contenido aquí: cualquier cambio de requisitos del pipeline se
edita en `specs/002-cicd-pipeline-branching/spec.md`, no en este archivo.
