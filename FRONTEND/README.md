# StreamHub — Frontend

React 19 + TypeScript + Vite 7 frontend for the StreamHub video streaming platform.

Full setup instructions live in the [root README](../README.md).

## Quick start

```bash
cp .env.example .env     # VITE_API_BASE_URL=http://localhost:2911/api/v1
npm install
npm run dev              # http://localhost:1304
```

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Vite dev server on port 1304 (proxies `/api`, `/oauth2` to the backend) |
| `npm run build` | Type-check (`tsc -b`) + production build to `dist/` |
| `npm run lint` | ESLint |
| `npm run preview` | Serve the production build locally |

## Environment

| Variable | Description |
|---|---|
| `VITE_API_BASE_URL` | Backend API base. `http://localhost:2911/api/v1` locally, `/api/v1` in Docker (nginx proxies it) |
| `VITE_BACKEND_URL` | Optional backend-origin override. Derived from `VITE_API_BASE_URL` if unset — used for OAuth2 redirect links |

Only `VITE_`-prefixed variables are exposed to the browser. Never put secrets here.

## Structure

```
src/
├── config/
│   ├── ApiClient.ts    axios instance, auth token interceptor, 401-refresh queue
│   └── env.ts          single source of truth for API/OAuth URLs
├── auth/
│   ├── store.ts        user session (Zustand + persist)
│   └── adminStore.ts   admin session (separate store + separate cookie)
├── service/            typed API wrappers
├── models/             TypeScript interfaces
├── components/
│   ├── auth/           RequireAuth, RequireAdmin route guards, OtpModal
│   ├── Video/          VideoPlayer (video.js + HLS), VideoCard, VideoUpload, CommentSection
│   ├── layout/         Navbar, Sidebar, AdminLayout, OAuth buttons
│   └── ui/             shadcn-style primitives
└── pages/
    ├── public/         Home, Login, Registration, WatchVideo, UserChannel, OAuth*
    ├── users/          UserHome, UserProfile, Upload, UserFeedback
    └── admin/          Dashboard, Users, Videos, Comments, Feedback, Categories, Login
```

## Route protection

Protected routes are wrapped in `<RequireAuth>` / `<RequireAdmin>` (see `src/main.tsx`).
Both guards verify the session with the **backend** before rendering — the persisted
`authStatus` / `adminStatus` flags alone are not trusted.

The access token is kept in memory only (Zustand, not persisted); the refresh token
lives in an `HttpOnly` cookie and is never readable from JavaScript.
