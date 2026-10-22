# ============================================================
# StreamHub Frontend — Multi-stage Docker build
# ============================================================

# ── Stage 1: Build ────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app

# Dependencies pehle cache karo
COPY package.json package-lock.json ./
RUN npm ci

# Build-time env vars — nginx reverse-proxy ke through relative paths use karenge
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_BACKEND_URL=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_BACKEND_URL=$VITE_BACKEND_URL

COPY . .
RUN npm run build

# ── Stage 2: Serve with Nginx ──────────────────────────────────
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
