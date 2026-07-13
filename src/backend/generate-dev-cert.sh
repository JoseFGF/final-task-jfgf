#!/usr/bin/env bash
# T056 (FR-019/SC-006): genera un keystore PKCS#12 autofirmado de DESARROLLO
# para habilitar HTTPS en el perfil `prod` (ver application-prod.yml).
#
# NOTA: este script ya NO hace falta ejecutarlo manualmente. El propio build
# de Maven (pom.xml, plugin maven-antrun-plugin, fase `generate-resources`)
# genera el keystore automáticamente en
# target/classes/certs/dev-keystore.p12 (classpath de runtime/test, cubierto
# por .gitignore vía target/) tanto en `mvn clean verify` local como dentro
# del Dockerfile. Se mantiene este script solo como referencia/documentación
# del comando keytool usado, y como utilidad opcional para inspeccionar o
# regenerar el keystore fuera del build (p.ej. para depurar el certificado
# con keytool -list).
#
# NO usar este certificado en un despliegue real: es autofirmado, sin CA de
# confianza, pensado únicamente para satisfacer FR-019 (cifrado en tránsito)
# en un entorno de curso/desarrollo. En un despliegue real se sustituiría por
# un certificado emitido por una CA (o terminación TLS en un proxy/balanceador
# gestionado, p.ej. con Let's Encrypt/ACM).
#
# Uso: ./generate-dev-cert.sh [ruta-salida] [password]
set -euo pipefail

OUT="${1:-target/classes/certs/dev-keystore.p12}"
PASSWORD="${2:-devchangeit}"

mkdir -p "$(dirname "$OUT")"

keytool -genkeypair \
  -alias fieldops-dev \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 \
  -keystore "$OUT" \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=localhost, OU=FieldOps Dev, O=FieldOps, L=Dev, ST=Dev, C=US" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

echo "Keystore generado en $OUT"
echo "Password: $PASSWORD (solo desarrollo — cambiar via SERVER_SSL_KEY_STORE_PASSWORD en despliegues reales)"
