# T056 (FR-019/FR-020): imagen de PostgreSQL con TLS habilitado usando un
# certificado autofirmado de desarrollo, generado dentro del propio build
# con openssl (mismo comando que documenta generate-dev-cert.sh) en vez de
# copiarlo desde el contexto de build: así el certificado nunca se commitea
# en el repo y se regenera en cada build de la imagen.
FROM postgres:16

RUN openssl req -x509 -newkey rsa:2048 -nodes \
      -keyout /var/lib/postgresql/server.key -out /var/lib/postgresql/server.crt -days 3650 \
      -subj "/CN=postgres/O=FieldOps Dev" \
      -addext "subjectAltName=DNS:postgres,DNS:localhost,IP:127.0.0.1" \
    && chown postgres:postgres /var/lib/postgresql/server.crt /var/lib/postgresql/server.key \
    && chmod 644 /var/lib/postgresql/server.crt \
    && chmod 600 /var/lib/postgresql/server.key

CMD ["postgres", "-c", "ssl=on", "-c", "ssl_cert_file=/var/lib/postgresql/server.crt", "-c", "ssl_key_file=/var/lib/postgresql/server.key"]
