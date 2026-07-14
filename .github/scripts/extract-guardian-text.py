"""Extrae el último bloque de texto del asistente de un execution_file JSONL
producido por anthropics/claude-code-action, para el guardián de constitución
(_constitution-guardian.yml) — evita el bug de validación AJV del SDK al no
depender de --json-schema/structured_output.
"""

import json
import sys

path = sys.argv[1]
last_text = ""
found_any_assistant_text = False

with open(path, encoding="utf-8") as f:
    raw = f.read()

for line in raw.splitlines():
    line = line.strip()
    if not line:
        continue
    try:
        msg = json.loads(line)
    except json.JSONDecodeError:
        continue
    if not isinstance(msg, dict):
        continue
    if msg.get("type") == "assistant":
        content = msg.get("message", {}).get("content", [])
        if isinstance(content, list):
            for block in content:
                if isinstance(block, dict) and block.get("type") == "text":
                    last_text = block.get("text", "")
                    found_any_assistant_text = True

# Respaldo: solo si el execution_file no tiene NINGÚN mensaje "assistant"
# con bloque de texto reconocible (formato inesperado del SDK) se busca el
# marcador en el contenido crudo. No se usa como respaldo genérico cuando sí
# hubo mensajes de asistente pero ninguno concluyó con el marcador (p. ej.
# el guardián se quedó sin turnos): en ese caso el propio contenido crudo
# —que incluye el prompt de este job y, en PRs que tocan este archivo, el
# diff de sus instrucciones— contiene igualmente las cadenas
# "CONSTITUTION_OK"/"CONSTITUTION_VIOLATION" de forma literal, y una
# búsqueda cruda las confundiría con una conclusión real (falso positivo
# ya observado). En ese caso se deja `last_text` tal cual (sin marcador),
# y el paso que llama a este script lo trata como fallo explícito
# ("el guardián no confirmó explícitamente CONSTITUTION_OK").
if not found_any_assistant_text:
    if "CONSTITUTION_VIOLATION" in raw:
        idx = raw.find("CONSTITUTION_VIOLATION")
        last_text = raw[idx : idx + 300]
    elif "CONSTITUTION_OK" in raw:
        last_text = "CONSTITUTION_OK"

print(last_text)
