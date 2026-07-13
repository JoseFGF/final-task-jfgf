#!/usr/bin/env bash
# PreToolUse gate: blocks Write/Edit under /src, /tests, /contracts until the
# Spec Kit artifacts for the active feature exist. Enforces "spec before code"
# mechanically. Reads the active feature directory from .specify/feature.json
# (maintained by /speckit-specify) rather than assuming a fixed flat layout,
# since spec.md/plan.md/tasks.md live per-feature under specs/<feature>/ and
# constitution.md lives under .specify/memory/.
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

gate="none"
case "$file" in
  */contracts/*|contracts/*) gate="contracts" ;;
  */src/*|src/*|*/tests/*|tests/*) gate="src" ;;
  *) exit 0 ;;
esac

root=$(pwd)
root_native=$(pwd -W 2>/dev/null || pwd)

emit_deny() {
  "$PY" -c "import json,sys; print(json.dumps({'hookSpecificOutput':{'hookEventName':'PreToolUse','permissionDecision':'deny','permissionDecisionReason':sys.argv[1]}}))" "$1"
  exit 0
}

constitution="$root/.specify/memory/constitution.md"
if [ ! -f "$constitution" ]; then
  emit_deny "Bloqueado: falta .specify/memory/constitution.md. Segun la disciplina Spec Kit del proyecto, no se debe tocar codigo/contrato antes de completar esa fase."
fi

feature_dir_native=$(printf '%s' "$root_native" | "$PY" -c "
import json, sys
root = sys.stdin.read().strip().rstrip('/')
fj = root + '/.specify/feature.json'
try:
    with open(fj, encoding='utf-8') as f:
        data = json.load(f)
    d = data.get('feature_directory', '').strip('/')
    print(root + '/' + d if d else '')
except Exception:
    print('')
")
# Translate back to a path bash's [ -f ] can use (native has a drive letter + backslashes-or-slashes; MSYS bash accepts C:/... fine for -f tests)
feature_dir="$feature_dir_native"

if [ -z "$feature_dir" ] || [ ! -d "$feature_dir" ]; then
  emit_deny "Bloqueado: no se encontro un feature_directory activo en .specify/feature.json. Ejecuta /speckit-specify primero."
fi

if [ ! -f "$feature_dir/spec.md" ]; then
  emit_deny "Bloqueado: falta spec.md en $feature_dir. Segun la disciplina Spec Kit del proyecto, no se debe tocar codigo/contrato antes de completar esa fase."
fi

if [ ! -f "$feature_dir/plan.md" ]; then
  emit_deny "Bloqueado: falta plan.md en $feature_dir. El contrato de la API y el codigo se escriben despues de /speckit-plan, no antes."
fi

if [ "$gate" = "src" ] && [ ! -f "$feature_dir/tasks.md" ]; then
  emit_deny "Bloqueado: falta tasks.md en $feature_dir. El codigo de src/ y tests/ se escribe durante /speckit-implement, a partir de las tareas de /speckit-tasks."
fi

exit 0
