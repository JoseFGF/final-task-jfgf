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
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# El perfil `prod` (application-prod.yml) expone HTTPS en el puerto 8443
# usando el keystore autofirmado de desarrollo, generado automáticamente
# durante `mvn package` (ver pom.xml, plugin maven-antrun-plugin) en
# target/classes/certs/dev-keystore.p12 y empaquetado dentro del jar junto
# con el resto de recursos de classpath, por lo que no se necesita copiar
# certificados por separado ni commitearlos en el repo.
COPY --from=build /build/target/fieldops-backend.jar app.jar

EXPOSE 8443

ENTRYPOINT ["java", "-jar", "app.jar"]
