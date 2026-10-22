/**
 * Frontend environment config — ek hi source of truth.
 *
 * PEHLE do alag knobs the:
 *   - VITE_API_BASE_URL  (host + /api/v1)  → ApiClient, VideoService
 *   - VITE_BACKEND_URL   (host only)       → Oauth2Buttons
 * Ek set karne se doosra chhoot jata tha (ya iska ulta) — OAuth ya API
 * chupke se break ho jata tha.
 *
 * AB: VITE_API_BASE_URL primary hai aur backend origin usse DERIVE hota hai.
 * VITE_BACKEND_URL sirf explicit override ke liye hai.
 */

// API base URL (e.g. "http://localhost:2911/api/v1" ya "/api/v1")
const rawApiBaseUrl: string =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:2911/api/v1";

export const API_BASE_URL = rawApiBaseUrl;

// Backend origin — API path hata kar nikaala gaya.
// "/api/v1" → "" (relative — nginx proxy karega)
// "http://localhost:2911/api/v1" → "http://localhost:2911"
const derivedOrigin = rawApiBaseUrl.replace(/\/api\/v1\/?$/, "");

// Explicit override (agar koi alag host chahiye to)
export const BACKEND_ORIGIN: string =
  import.meta.env.VITE_BACKEND_URL !== undefined &&
  import.meta.env.VITE_BACKEND_URL !== ""
    ? import.meta.env.VITE_BACKEND_URL
    : derivedOrigin;

// OAuth2 authorization URL (backend origin + path)
export const oauth2Url = (provider: "google" | "github"): string =>
  `${BACKEND_ORIGIN}/oauth2/authorization/${provider}`;

// HLS master playlist URL
export const hlsMasterUrl = (videoId: string): string =>
  `${API_BASE_URL}/videos/${videoId}/master.m3u8`;

// Thumbnail URL
export const thumbnailUrl = (videoId: string): string =>
  `${API_BASE_URL}/videos/${videoId}/thumbnail.jpg`;
