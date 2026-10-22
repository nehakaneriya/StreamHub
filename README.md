# VideoStreamApp

A full-stack video streaming platform — Spring Boot 3.5 (Java 21) backend, React 19 + TypeScript + Vite frontend, MySQL + Redis, FFmpeg-based adaptive HLS transcoding.

---

## Features

| Area | Details |
|---|---|
| **Auth** | Email/password + OTP email verification, Google & GitHub OAuth2, JWT access tokens, `HttpOnly` refresh-token cookies, role-based access (USER / ADMIN) |
| **Video** | Upload → FFmpeg multi-resolution HLS transcode (1080p/720p/480p/240p) → adaptive `master.m3u8` playback via video.js, server-generated thumbnails, HTTP range streaming |
| **Social** | Comments + one-level replies, categories, feedback, public user channels, view counts (30 s watch threshold) |
| **Admin** | Dashboard stats, user & video management, role assignment, comment moderation (hide/unhide), category management |
| **Infra** | Docker Compose (MySQL, Redis, backend, nginx), Redis caching with keyset pagination, brute-force rate limiting |

---

## Tech stack

**Backend** — Java 21, Spring Boot 3.5.7, Spring Security, Spring Data JPA, MySQL 8, Redis (Jedis), JJWT, Lombok, ModelMapper, FFmpeg
**Frontend** — React 19, TypeScript, Vite 7, Tailwind CSS 4, Zustand, Axios, video.js, react-router-dom 7, react-hot-toast

---

## Prerequisites

- **JDK 21**, **Maven 3.9+**, **Node 18+**
- **MySQL 8** and **Redis** running locally
- **FFmpeg** on `PATH` (must include `ffprobe`)

```bash
# FFmpeg check
ffmpeg -version && ffprobe -version
```

---

## Setup — Local development

### 1. Backend

```bash
cd BACKEND
cp .env.example .env      # values fill karo
mvn spring-boot:run
```

Runs on **http://localhost:2911** (dev profile). The `dev` profile reads `BACKEND/.env`.

### 2. Frontend

```bash
cd FRONTEND
cp .env.example .env      # VITE_API_BASE_URL=http://localhost:2911/api/v1
npm install
npm run dev
```

Runs on **http://localhost:1304** (Vite dev server).

### 3. Database

Hibernate `ddl-auto: update` creates the schema on first boot. Roles (`ROLE_USER`, `ROLE_ADMIN`) and the default admin are seeded automatically.

---

## Setup — Docker Compose

```bash
cp .env.example .env      # root .env — docker-compose vars ke liye
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend (nginx) | http://localhost · http://localhost:1304 |
| Backend API | http://localhost:2911/api/v1 |
| MySQL | localhost:3307 |
| Redis | localhost:6379 |

---

## Environment variables

### Root `.env` (Docker Compose)

| Variable | Default | Notes |
|---|---|---|
| `DB_NAME` / `DB_PASSWORD` | `videostreamdb` / `root` | MySQL |
| `REDIS_PASSWORD` | `videostream_redis_pass` | Redis auth (also set in `BACKEND/.env` for local Redis) |
| `JWT_SECRET` | *placeholder* | **64+ random chars** — generate with `openssl rand -base64 48` |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@gmail.com` / — | Password policy: 8+ chars, 1 letter, 1 digit, 1 symbol |
| `ADMIN_SEED_ENABLED` | `true` | `false` → default admin create nahi hoga |
| `GOOGLE_CLIENT_ID` / `_SECRET` | — | OAuth2 |
| `GITHUB_CLIENT_ID` / `_SECRET` | — | OAuth2 |
| `MAIL_*` | — | SMTP for OTP emails |
| `OTP_LOG_ENABLED` | `false` | `true` (dev only) → real random OTP console me log hota hai, taaki SMTP ke bina bhi flow test ho sake |
| `RATE_LIMIT_*` | see `.env.example` | Brute-force protection |

### `BACKEND/.env` (local dev)

Same variables, plus `DB_URL`, `DB_USERNAME`, `REDIS_HOST`, `REDIS_PORT`, `FRONT_END_URL`, `FRONT_END_*_REDIRECT`.

### `FRONTEND/.env`

| Variable | Example | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:2911/api/v1` | Primary. Docker me `/api/v1` (relative) — nginx proxy karta hai |
| `VITE_BACKEND_URL` | `http://localhost:2911` | Optional override. Khud derive hota hai `VITE_API_BASE_URL` se |

> Only `VITE_`-prefixed vars are exposed to the browser. Never put a secret in `FRONTEND/.env`.

---

## API surface

```
POST   /api/v1/auth/register          POST   /api/v1/auth/verify-otp
POST   /api/v1/auth/resend-otp        POST   /api/v1/auth/cancel-registration
POST   /api/v1/auth/login             POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout            GET    /api/v1/auth/me
POST   /api/v1/auth/admin/login       POST   /api/v1/auth/admin/refresh
POST   /api/v1/auth/admin/logout

GET    /api/v1/videos                 GET    /api/v1/videos/{id}
POST   /api/v1/videos                 PUT    /api/v1/videos/{id}
DELETE /api/v1/videos/{id}            GET    /api/v1/videos/my-videos
GET    /api/v1/videos/user/{userId}   POST   /api/v1/videos/{id}/view
GET    /api/v1/videos/{id}/master.m3u8
GET    /api/v1/videos/{id}/thumbnail.jpg
GET    /api/v1/videos/{id}/{res}/playlist.m3u8
GET    /api/v1/videos/{id}/{res}/{seg}.ts

GET    /api/v1/videos/{id}/comments   POST   /api/v1/videos/{id}/comments
DELETE /api/v1/comments/{id}          GET    /api/v1/categories
POST   /api/v1/feedback

GET    /api/v1/users/{id}             PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}

GET    /api/v1/admin/dashboard        GET    /api/v1/admin/users
GET    /api/v1/admin/videos           GET    /api/v1/admin/comments
DELETE /api/v1/admin/comments/{id}    GET    /api/v1/admin/feedbacks
POST   /api/v1/admin/users/{id}/assign-admin
DELETE /api/v1/admin/users/{id}/remove-admin
```

Swagger UI (dev/QA only): http://localhost:2911/swagger-ui.html

---

## Security notes

- **Never commit `.env`.** Both `.env` files are gitignored.
- **Admin password** must satisfy the policy or the app refuses to start (fail fast).
- If `JWT_SECRET` is unset, the **dev** profile generates a random ephemeral secret at startup (tokens invalidate on restart) and logs a warning. QA/prod **fail fast** instead.
- Har OTP hamesha **random** hota hai (koi fixed/universal code nahi). `OTP_LOG_ENABLED` dev me random OTP log karta hai (testing ke liye), prod me hamesha `false`.
- Rate limits: 5 failed logins → 15 min lock, 5 OTP attempts, 3 OTP resends/hour.

---

## Project structure

```
BACKEND/src/main/java/com/neha/VideoStreamApp/
├── config/       SecurityConfig, AppConstants, ProjectConfig
├── controller/   REST endpoints
├── service/      business logic (+ impl/)
├── security/     JwtService, JwtAuthenticationFilter, OAuth2SuccessHandler, CookieService
├── cache/        Redis: user cache, video scroll cache, JWT blacklist
├── redis/        key generation + Redis config
├── entity/       JPA entities
├── repositories/ Spring Data JPA
├── dtos/         request/response DTOs
├── helper/       ScrollPositionCodec, UserHelper
└── exception/    GlobalExceptionHandler + typed exceptions

FRONTEND/src/
├── config/       ApiClient (axios + interceptors), env.ts
├── auth/         Zustand stores (user + admin)
├── service/      API wrappers
├── pages/        public / users / admin
├── components/   Video, auth (incl. RequireAuth, RequireAdmin), layout, ui
└── models/       TypeScript types
```
