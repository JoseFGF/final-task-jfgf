---
name: ai-eval-harness
description: Genera y mantiene /evals para el componente de IA (asistente que resume la incidencia de una orden a partir de las notas del técnico), con golden cases y umbrales de aceptación. Úsalo cuando el usuario pida "crea las evals del asistente de IA", "genera golden cases", o durante /speckit.implement al tocar el componente de resumen. No implementa el componente de IA, solo sus evals.
---

# AI Eval Harness

Esta skill construye el arnés de evaluación del componente de IA como contrato verificable, no implementa el componente en sí (eso corresponde a java-expert u otro agente según dónde viva el componente).

## Pasos

1. **Localizar el contrato del componente**: lee la spec (`.specify/spec.md`) para confirmar entradas (notas del técnico, evidencia asociada a la orden), salida (resumen de la incidencia) y la regla dura: **si no hay evidencia/nota suficiente, el asistente debe decirlo explícitamente y no inventar un resumen**.

2. **Generar/actualizar `/evals`** con golden cases cubriendo al menos:
   - **Caso feliz**: nota clara y suficiente → el resumen generado debe reflejar fielmente el contenido, sin añadir información que no esté en la nota
   - **Sin evidencia**: nota vacía o inexistente → el asistente debe responder indicando que no hay evidencia suficiente, nunca producir un resumen inventado
   - **Evidencia insuficiente/ambigua**: nota muy corta, contradictoria o poco específica → debe señalar la insuficiencia en vez de rellenar huecos con suposiciones
   - **Casos límite adicionales** relevantes al dominio (múltiples notas, notas en otro idioma, notas con ruido/typos) si la spec los contempla

   Cada golden case incluye: input, output esperado (o criterio de aceptación si no es exacto), y la razón de por qué ese caso es relevante.

3. **Definir umbrales de aceptación**:
   - Qué % de golden cases debe pasar para considerar el componente aceptable
   - Cómo se juzga un "pass" cuando la salida no es determinista (ej. criterio de contenido mínimo esperado, ausencia de afirmaciones no presentes en la nota, presencia obligatoria del aviso de "evidencia insuficiente" cuando aplique)
   - Trata como **fallo crítico** cualquier caso donde el asistente invente contenido no respaldado por la nota, incluso si el umbral global se cumple

4. **Dejar un mecanismo para ejecutar las evals**: script simple o instrucciones claras de cómo correrlas y cómo se reporta pass/fail contra el umbral, para que quede integrado con el ciclo de test del proyecto.

## Reglas

- La regla de "no inventar sin evidencia" es la prioridad número uno de este arnés; un componente que falla ahí no pasa aunque acierte en los demás casos.
- No implementes el componente de IA; solo el contrato de evaluación. Si detectas que el componente aún no existe, deja las evals listas para cuando se implemente.
- No inventes golden cases desconectados de la spec real; basa cada caso en lo que el brief/spec dice sobre el dominio (órdenes, notas de técnico, evidencia fotográfica).
