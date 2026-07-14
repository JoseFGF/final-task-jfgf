# --- Build stage ---
# El build-helper-maven-plugin del pom.xml añade tests/{unit,contract,
# integration} (fuera de src/backend/) como test-sources, pero solo se
# usan en la fase `test`. El empaquetado de producción no ejecuta tests
# (-DskipTests) y el compilador de Maven ignora silenciosamente los
# test-sources configurados que no existen, así que no hace falta copiar
# ese directorio al contexto de build (context: ./src/backend).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copia primero el pom.xml para cachear la resolución de dependencias.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# --- Runtime stage ---
# Variante Alpine: la imagen eclipse-temurin:21-jre (Ubuntu/chiselled) trae
# el gestor de servicios "pebble" de Canonical con dependencias Go
# desactualizadas (golang.org/x/net) — 7 CVEs HIGH detectados por Trivy sin
# relación con nuestro código. Alpine no incluye ese binario.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# La imagen base congela los paquetes de Alpine a la fecha de su propio
# build; actualizarlos aquí asegura los parches de seguridad ya publicados
# (libexpat, p11-kit — CVEs HIGH detectados por Trivy) sin depender de que
# el mantenedor de la imagen base publique una nueva revisión.
RUN apk update && apk upgrade --no-cache

# El perfil `prod` (application-prod.yml) expone HTTPS en el puerto 8443
# usando el keystore autofirmado de desarrollo, generado automáticamente
# durante `mvn package` (ver pom.xml, plugin maven-antrun-plugin) en
# target/classes/certs/dev-keystore.p12 y empaquetado dentro del jar junto
# con el resto de recursos de classpath, por lo que no se necesita copiar
# certificados por separado ni commitearlos en el repo.
COPY --from=build /build/target/fieldops-backend.jar app.jar

EXPOSE 8443

ENTRYPOINT ["java", "-jar", "app.jar"]
