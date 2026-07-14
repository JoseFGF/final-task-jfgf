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
    for line in f:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            continue
        if msg.get("type") == "assistant":
            content = msg.get("message", {}).get("content", [])
            for block in content:
                if block.get("type") == "text":
                    last_text = block.get("text", "")

print(last_text)
