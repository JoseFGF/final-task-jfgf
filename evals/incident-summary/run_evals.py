#!/usr/bin/env python3
"""
Eval runner para el componente de resumen de incidencia (US5, FR-015/FR-016).

Ejercita el SYSTEM_PROMPT real de
src/backend/src/main/java/com/fieldops/ai/AnthropicClient.java contra la API
de Anthropic, usando los golden cases de golden-cases.yaml. No sustituye a
los tests de código (T047-T050, en tests/), que mockean AnthropicClient y
prueban la orquestación/fail-safe del backend con datos deterministas — esto
prueba el modelo+prompt en sí.

Requiere: ANTHROPIC_API_KEY en el entorno. Costo real por ejecución (llamadas
a la API) — no se ejecuta como parte de `mvn test` por defecto; se corre
manualmente o como paso explícito de CI antes de un merge (ver README.md).

IMPORTANTE: si el SYSTEM_PROMPT o el modelo cambian en AnthropicClient.java,
actualiza las constantes de abajo para que este arnés siga siendo fiel al
comportamiento real en producción.
"""

import json
import os
import re
import sys
import urllib.request

import yaml

ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
ANTHROPIC_VERSION = "2023-06-01"
MODEL = "claude-3-5-haiku-20241022"
MAX_TOKENS = 300

# Debe coincidir exactamente con AnthropicClient.SYSTEM_PROMPT
SYSTEM_PROMPT = (
    "Resume brevemente la incidencia a partir de esta nota; si la nota no tiene "
    "informacion suficiente para un resumen util, responde exactamente con el "
    "token INSUFFICIENT_EVIDENCE y nada mas - no inventes contenido. Cuando sí "
    "haya informacion suficiente, el resumen debe ser breve (unas pocas frases, "
    "no una reescritura extensa) y debe estar escrito en el mismo idioma que la "
    "nota original."
)
INSUFFICIENT_TOKEN = "INSUFFICIENT_EVIDENCE"

# Umbral de aceptación (ver README.md para el razonamiento)
PASS_THRESHOLD = 0.8

ENGLISH_MARKERS = [" the ", " and ", " was ", " is ", " with ", " leak"]
SPANISH_MARKERS = [" el ", " la ", " que ", " con ", " fue ", " está"]


def call_anthropic(note: str) -> str:
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        raise RuntimeError("ANTHROPIC_API_KEY no está configurada en el entorno")

    body = json.dumps(
        {
            "model": MODEL,
            "max_tokens": MAX_TOKENS,
            "system": SYSTEM_PROMPT,
            "messages": [{"role": "user", "content": note}],
        }
    ).encode("utf-8")

    req = urllib.request.Request(
        ANTHROPIC_API_URL,
        data=body,
        headers={
            "x-api-key": api_key,
            "anthropic-version": ANTHROPIC_VERSION,
            "content-type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    return payload["content"][0]["text"]


def is_insufficient(response_text: str) -> bool:
    return (not response_text.strip()) or INSUFFICIENT_TOKEN in response_text


def looks_like_language(text: str, language: str) -> bool:
    lowered = f" {text.lower()} "
    markers = ENGLISH_MARKERS if language == "en" else SPANISH_MARKERS
    return any(m in lowered for m in markers)


def evaluate_case(case: dict) -> dict:
    case_id = case["id"]

    if case.get("skip_api_call"):
        return {
            "id": case_id,
            "status": "SKIPPED",
            "detail": case["reason"].strip(),
        }

    try:
        response_text = call_anthropic(case["note"])
    except Exception as exc:  # noqa: BLE001 - queremos capturar cualquier fallo de red/API
        return {"id": case_id, "status": "ERROR", "detail": f"Fallo llamando a la API: {exc}"}

    insufficient = is_insufficient(response_text)
    expect_insufficient = case["expect"] == "insufficient"

    # Regla #1, no negociable: si se esperaba insuficiencia y el modelo
    # generó contenido igualmente, es un FALLO CRÍTICO (alucinación),
    # independientemente del umbral global.
    if expect_insufficient and not insufficient:
        return {
            "id": case_id,
            "status": "CRITICAL_FAIL",
            "detail": (
                f"Se esperaba evidencia insuficiente pero el modelo generó "
                f"contenido: {response_text!r}"
            ),
        }

    if not expect_insufficient and insufficient:
        return {
            "id": case_id,
            "status": "FAIL",
            "detail": "Se esperaba un resumen pero el modelo declaró evidencia insuficiente.",
        }

    if expect_insufficient and insufficient:
        return {"id": case_id, "status": "PASS", "detail": "Insuficiencia detectada correctamente."}

    # A partir de aquí: se esperaba y se obtuvo un resumen. Verificaciones
    # heurísticas automáticas; lo que no se pueda verificar con certeza queda
    # en REVIEW para un humano, no se auto-aprueba a ciegas.
    lowered = response_text.lower()
    checks = []

    for term in case.get("must_mention_any", []):
        if term.lower() in lowered:
            checks.append(("must_mention_any", True, term))
            break
    else:
        if case.get("must_mention_any"):
            checks.append(("must_mention_any", False, case["must_mention_any"]))

    for forbidden in case.get("must_not_mention_any", []):
        if forbidden.lower() in lowered:
            checks.append(("must_not_mention_any", False, forbidden))

    if "language" in case and not looks_like_language(response_text, case["language"]):
        checks.append(("language", False, case["language"]))

    if "max_sentences" in case:
        sentence_count = len(re.findall(r"[.!?]+", response_text))
        if sentence_count > case["max_sentences"]:
            checks.append(("max_sentences", False, f"{sentence_count} > {case['max_sentences']}"))

    failed_checks = [c for c in checks if c[1] is False]
    if failed_checks:
        return {
            "id": case_id,
            "status": "REVIEW",
            "detail": (
                f"Resumen generado pero con heurísticas fallidas {failed_checks}; "
                f"revisar manualmente: {response_text!r}"
            ),
        }

    return {"id": case_id, "status": "PASS", "detail": f"Resumen: {response_text!r}"}


def main() -> int:
    cases_path = os.path.join(os.path.dirname(__file__), "golden-cases.yaml")
    with open(cases_path, encoding="utf-8") as f:
        cases = yaml.safe_load(f)["cases"]

    results = [evaluate_case(c) for c in cases]

    counted = [r for r in results if r["status"] not in ("SKIPPED",)]
    critical = [r for r in counted if r["status"] == "CRITICAL_FAIL"]
    passed = [r for r in counted if r["status"] == "PASS"]
    review = [r for r in counted if r["status"] == "REVIEW"]
    errors = [r for r in counted if r["status"] == "ERROR"]

    for r in results:
        print(f"[{r['status']}] {r['id']}: {r['detail']}")

    rate = len(passed) / len(counted) if counted else 1.0
    print(f"\nPass rate: {len(passed)}/{len(counted)} = {rate:.0%} (umbral: {PASS_THRESHOLD:.0%})")
    print(f"Revisión manual pendiente: {len(review)}")
    print(f"Errores de ejecución (red/API): {len(errors)}")

    if critical:
        print(f"\nFALLO CRÍTICO: {len(critical)} caso(s) con posible alucinación. Bloqueante.")
        return 1

    if errors:
        print("\nNo se puede evaluar el umbral: hubo errores de red/API. Reintentar.")
        return 2

    if rate < PASS_THRESHOLD:
        print("\nFALLO: pass rate por debajo del umbral.")
        return 1

    if review:
        print("\nUmbral cumplido, pero hay casos en REVIEW: requieren confirmación humana antes de dar el eval por verde.")
        return 3

    print("\nEval en verde.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
