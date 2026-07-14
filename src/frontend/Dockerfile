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
# /etc/nginx/templates/*.template -> /etc/nginx/conf.d/*.conf). Los valores
# por defecto reproducen el docker-compose local (backend accesible como
# host "backend:8443" vía el DNS embebido de Docker). En Railway se
# sobreescriben BACKEND_ORIGIN y DNS_RESOLVER como variables de entorno del
# servicio frontend, sin tocar esta imagen.
ENV DNS_RESOLVER=127.0.0.11 \
    BACKEND_SCHEME=https \
    BACKEND_ORIGIN=backend:8443
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80
