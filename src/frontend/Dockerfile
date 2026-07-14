# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /build

# package-lock.json (lockfileVersion 3) fue generado con npm 11.x; el npm
# 10.8.2 que trae la imagen node:20-alpine falla en `npm ci` al no
# encontrar resueltas algunas dependencias opcionales (p.ej. chokidar@5.0.0)
# que npm 11 sí satisface. Se actualiza npm antes de instalar para que
# `npm ci` sea reproducible con el lockfile tal cual está commiteado.
RUN npm install -g npm@11

# Copia primero los manifiestos de dependencias para cachear `npm ci` en
# capas de Docker separadas del resto del código fuente.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Runtime stage ---
FROM nginx:alpine AS runtime

# El builder "application" de Angular 18 (angular.json) genera el bundle en
# dist/frontend/browser (Angular 17+ separa server/browser aunque no se use
# SSR aquí).
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

# nginx.conf.template se resuelve con envsubst al arrancar el contenedor
# (mecanismo estándar de la imagen oficial nginx:alpine para archivos en
# /etc/nginx/templates/*.template -> /etc/nginx/conf.d/*.conf). El valor por
# defecto de BACKEND_ORIGIN reproduce el docker-compose local (backend
# accesible como host "backend:8443"). En Railway se sobreescribe
# BACKEND_ORIGIN como variable de entorno del servicio frontend, sin tocar
# esta imagen. NGINX_ENTRYPOINT_LOCAL_RESOLVERS activa el script propio de
# la imagen oficial que detecta el resolver DNS real del contenedor (DNS
# embebido de Docker en compose, DNS interno en Railway) en vez de
# hardcodear un IP público que puede no ser alcanzable por egress
# restringido en la red privada del PaaS.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1 \
    BACKEND_SCHEME=https \
    BACKEND_ORIGIN=backend:8443
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80
