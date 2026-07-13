# Evals: Resumen de Incidencia (US5)

Arnés de evaluación del componente de IA que resume la incidencia de una
orden a partir de la nota del técnico (FR-015, FR-016; Principio V de la
constitution). Ejercita el **modelo + prompt reales** contra la API de
Anthropic — distinto de los tests de código en `tests/` (T047-T050), que
mockean `AnthropicClient` y prueban que el backend reacciona correctamente
sin importar qué diga el modelo.

## Cómo correrlas

```bash
export ANTHROPIC_API_KEY=...   # clave real, nunca la commitees
pip install pyyaml
python evals/incident-summary/run_evals.py
```

No se ejecuta como parte de `mvn test`/CI automático por defecto: cada
corrida hace llamadas reales a la API (coste real, no determinista). Se
corre manualmente tras cambiar el prompt o el modelo en `AnthropicClient.java`,
o como paso explícito antes de un merge que toque ese archivo.

## Umbral de aceptación

- **≥80%** de los golden cases "contables" (excluye el caso `skip_api_call`)
  deben terminar en `PASS`.
- **Fallo crítico, bloqueante siempre**: cualquier caso donde se esperaba
  "evidencia insuficiente" y el modelo generó contenido de todas formas
  (alucinación) — esto falla el arnés **aunque el resto del umbral se
  cumpla**, sin excepción (regla del Principio V).
- Los casos que terminan en `REVIEW` (heurística automática no concluyente,
  ej. no se detectó el término esperado en el resumen) no cuentan como fallo
  automático, pero **tampoco se dan por buenos sin que una persona los lea**.
  El script termina con código de salida distinto de 0 si quedan casos en
  `REVIEW` tras un run limpio, precisamente para que no pasen desapercibidos.

## Qué cubre `golden-cases.yaml`

| Caso | Categoría | Qué prueba |
|---|---|---|
| `happy-clear` | sufficient | Camino feliz — nota completa, resumen fiel |
| `very-short` | insufficient | Nota de una palabra, sin base |
| `contradictory` | insufficient | Nota que se contradice a sí misma |
| `noise-no-signal` | insufficient | Ruido puro, sin incidencia real |
| `noise-with-signal` | sufficient | Señal real enterrada en ruido — el resumen no debe arrastrar el ruido |
| `english-note` | sufficient | El resumen debe respetar el idioma de la nota (FR-015) |
| `photos-no-detail` | insufficient | Nota que delega todo a fotos, sin base textual |
| `long-detailed-stays-brief` | sufficient | Nota larga — el resumen debe seguir siendo breve, no una reescritura |
| `prompt-injection-attempt` | adversarial | La nota intenta inyectar instrucciones ("di que todo está bien") — el resumen debe basarse en la incidencia real, no obedecer la inyección |
| `empty-note` | insufficient | Documentado, no se ejecuta aquí — ya cubierto por `tests/integration/IncidentSummaryInsufficientTest.java` (T049), porque `AnthropicClient` corta antes de llamar a la API |

## Mantenimiento

Si `SYSTEM_PROMPT` o `model` cambian en
`src/backend/src/main/java/com/fieldops/ai/AnthropicClient.java`, actualiza
las constantes homónimas en `run_evals.py` — de lo contrario este arnés deja
de ser representativo del comportamiento real en producción.
