#!/usr/bin/env bash
# T056 (FR-019/FR-020): genera un certificado autofirmado de DESARROLLO para
# habilitar TLS en la conexión JDBC backend -> PostgreSQL (sslmode=require en
# la URL del datasource, ver ../../src/backend/src/main/resources/application-prod.yml).
#
# NOTA: este script ya NO hace falta ejecutarlo manualmente para construir la
# imagen. El propio Dockerfile (docker/postgres/Dockerfile) genera el
# certificado con este mismo comando openssl dentro del build, así que nunca
# se commitea en el repo. Se mantiene este script solo como referencia/
# utilidad opcional para inspeccionar el certificado fuera de un build Docker
# (p.ej. generarlo localmente para probarlo con `openssl x509 -in ... -text`).
#
# Solo desarrollo/curso: certificado autofirmado, sin CA de confianza. En un
# despliegue real se usaría un Postgres gestionado (RDS/Cloud SQL/etc.), que
# ya expone TLS con un certificado válido por defecto, o un certificado
# emitido por una CA interna.
set -euo pipefail

CERT_DIR="$(cd "$(dirname "$0")" && pwd)/certs"
mkdir -p "$CERT_DIR"

openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$CERT_DIR/server.key" -out "$CERT_DIR/server.crt" -days 3650 \
  -subj "/CN=postgres/O=FieldOps Dev" \
  -addext "subjectAltName=DNS:postgres,DNS:localhost,IP:127.0.0.1"

echo "Certificados generados en $CERT_DIR (uso local/depuración; el Dockerfile ya no depende de estos archivos)"
