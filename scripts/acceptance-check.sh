#!/usr/bin/env bash
# Script propio de verificación de criterios de aceptación contra la API
# real (FR-002 de specs/002-cicd-pipeline-branching/spec.md). No sustituye a
# los tests de Maven (tests/contract, tests/integration): estos son
# comprobaciones HTTP negras contra una instancia real ya arrancada del
# backend (vía docker compose), usando las credenciales de seed reales
# (src/backend/src/main/resources/db/migration/V2/V3__seed_*.sql).
set -euo pipefail

BASE_URL="${BASE_URL:-https://localhost:8443/api/v1}"
CURL="curl -sk --max-time 10"

fail() {
  echo "FALLO: $1" >&2
  exit 1
}

echo "Esperando a que el backend responda en $BASE_URL ..."
for i in $(seq 1 30); do
  CODE=$($CURL -o /dev/null -w '%{http_code}' "$BASE_URL/orders" || echo "000")
  if [ "$CODE" = "401" ]; then
    echo "Backend arriba (GET /orders sin token respondió 401 como se espera)."
    break
  fi
  if [ "$i" = "30" ]; then
    fail "el backend no respondió tras 60s de espera"
  fi
  sleep 2
done

echo "1) Login con credenciales de seed válidas (dispatcher@fieldops.test) debe devolver 200 + token"
LOGIN_RESPONSE=$($CURL -X POST "$BASE_URL/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"dispatcher@fieldops.test","password":"password123"}')
DISPATCHER_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
[ -n "$DISPATCHER_TOKEN" ] || fail "login de dispatcher no devolvió token: $LOGIN_RESPONSE"

echo "2) Login con password incorrecta debe devolver 401"
STATUS=$($CURL -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"dispatcher@fieldops.test","password":"password-incorrecta"}')
[ "$STATUS" = "401" ] || fail "login con password incorrecta devolvió $STATUS, se esperaba 401"

echo "3) GET /orders sin token debe devolver 401 (FR-017 de 001-order-lifecycle-workflow)"
STATUS=$($CURL -o /dev/null -w '%{http_code}' "$BASE_URL/orders")
[ "$STATUS" = "401" ] || fail "GET /orders sin token devolvió $STATUS, se esperaba 401"

echo "4) GET /orders con token de dispatcher debe devolver 200"
STATUS=$($CURL -o /dev/null -w '%{http_code}' "$BASE_URL/orders" -H "Authorization: Bearer $DISPATCHER_TOKEN")
[ "$STATUS" = "200" ] || fail "GET /orders con token válido devolvió $STATUS, se esperaba 200"

echo "5) TECHNICIAN intentando reasignar una orden debe devolver 403 (RBAC, FR-014)"
TECH_LOGIN=$($CURL -X POST "$BASE_URL/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"technician@fieldops.test","password":"password123"}')
TECH_TOKEN=$(echo "$TECH_LOGIN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
[ -n "$TECH_TOKEN" ] || fail "login de technician no devolvió token: $TECH_LOGIN"

STATUS=$($CURL -o /dev/null -w '%{http_code}' -X POST \
  "$BASE_URL/orders/a2222222-2222-2222-2222-222222222222/reassignment" \
  -H "Authorization: Bearer $TECH_TOKEN" -H 'Content-Type: application/json' \
  -d '{"newTechnicianEmail":"technician2@fieldops.test"}')
[ "$STATUS" = "403" ] || fail "TECHNICIAN reasignando una orden devolvió $STATUS, se esperaba 403"

echo "Todos los criterios de aceptación verificados contra la API real."
