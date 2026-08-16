# Multi-stage build (Phase 15) - the final image is nginx serving a static build,
# never Node itself.

# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /build

# package.json/package-lock.json copied (and `npm ci` run) before the rest of the source,
# so a plain code change doesn't force reinstalling every dependency.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite inlines VITE_* variables into the built JS at build time, not read at container
# startup - these must be the browser-reachable address (the docker-compose host-published
# port), not the internal service DNS name only other containers can resolve. Defaults here
# match docker-compose.yml's own defaults exactly.
ARG VITE_API_BASE_URL=http://localhost:8080/api
ARG VITE_WS_BASE_URL=ws://localhost:8080/ws
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_WS_BASE_URL=${VITE_WS_BASE_URL}
RUN npm run build

# ---- Runtime stage ----
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=10 \
    CMD wget -qO- http://localhost:80/ >/dev/null || exit 1
