#!/usr/bin/env bash
# PreToolUse gate: blocks Write/Edit under /src, /tests, /contracts until
# the Spec Kit artifacts exist. Enforces "spec before code" mechanically.
PY=""
for candidate in python3 python "/c/Python314/python"; do
  if out=$(command "$candidate" -c "print(1)" 2>/dev/null) && [ "$out" = "1" ]; then
    PY="$candidate"
    break
  fi
done
if [ -z "$PY" ]; then
  exit 0
fi

input=$(cat)
file=$(printf '%s' "$input" | "$PY" -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('file_path',''))")
file=${file//\\//}

case "$file" in
  */src/*|src/*|*/tests/*|tests/*|*/contracts/*|contracts/*) ;;
  *) exit 0 ;;
esac

root=$(pwd)
specdir=""
for d in ".specify" "specs"; do
  if [ -d "$root/$d" ]; then
    specdir="$root/$d"
    break
  fi
done

emit_deny() {
  "$PY" -c "import json,sys; print(json.dumps({'hookSpecificOutput':{'hookEventName':'PreToolUse','permissionDecision':'deny','permissionDecisionReason':sys.argv[1]}}))" "$1"
}

if [ -z "$specdir" ]; then
  emit_deny "Bloqueado: no existe .specify/ ni /specs con los artefactos de spec. Segun la disciplina Spec Kit del proyecto, no se debe tocar codigo/contrato antes de completar esas fases."
  exit 0
fi

for artifact in constitution.md spec.md clarify.md checklist.md plan.md tasks.md; do
  if [ ! -f "$specdir/$artifact" ]; then
    emit_deny "Bloqueado: falta $artifact en $specdir. Segun la disciplina Spec Kit del proyecto, no se debe tocar codigo/contrato antes de completar esa fase."
    exit 0
  fi
done

exit 0
