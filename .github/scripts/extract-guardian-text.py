"""Extrae el último bloque de texto del asistente de un execution_file JSONL
producido por anthropics/claude-code-action, para el guardián de constitución
(_constitution-guardian.yml) — evita el bug de validación AJV del SDK al no
depender de --json-schema/structured_output.
"""

import json
import sys

path = sys.argv[1]
last_text = ""

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

# Respaldo: si no se pudo reconstruir el mensaje estructurado (formato
# del execution_file no es el esperado), busca el marcador directamente
# en el contenido crudo del archivo.
if "CONSTITUTION_OK" not in last_text and "CONSTITUTION_VIOLATION" not in last_text:
    if "CONSTITUTION_VIOLATION" in raw:
        idx = raw.find("CONSTITUTION_VIOLATION")
        last_text = raw[idx : idx + 300]
    elif "CONSTITUTION_OK" in raw:
        last_text = "CONSTITUTION_OK"

print(last_text)
