import useAuthStore from "@/auth/store";
import useAdminStore from "@/auth/adminStore";
import { refreshToken, adminRefreshToken } from "@/service/Authservice";
import { API_BASE_URL } from "@/config/env";
import axios from "axios";


const apiClient = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        "Content-Type": "application/json",
    },
    withCredentials: true,
    // Default timeout 60s — pehle timeout: 0 (unlimited) tha jisse ek hung
    // backend request UI ko indefinitely spin karte rahi. Upload jaise
    // long-running requests per-call override kar sakte hain.
    timeout: 60_000,
});

/**
 * Admin request detection.
 *
 * SECURITY/CORRECTNESS: Pehle `url.includes("/admin")` use hota tha — jo
 * (a) user-controlled path segment me "/admin" hone par false positive deta tha,
 * aur (b) admin aur user ke liye SAME refresh queue use karta tha, jisse ek
 * admin request user token ke saath replay ho jata tha (403) aur vice versa.
 *
 * Ab alag-alag queues hain aur URL ka path prefix match hota hai.
 */
const ADMIN_PATH_PREFIX = "/admin";

function isAdminRequest(url: string | undefined): boolean {
    if (!url) return false;
    // URL se query string hatao, phir path ka prefix dekho
    const pathOnly = url.split("?")[0];
    return pathOnly === ADMIN_PATH_PREFIX
        || pathOnly.startsWith(ADMIN_PATH_PREFIX + "/")
        || pathOnly.includes("/auth/admin/");
}

// JWT automatically attach karega
apiClient.interceptors.request.use((config) => {
    const url = config.url || "";
    const adminRequest = isAdminRequest(url);

    const adminToken = useAdminStore.getState().adminToken;
    const userToken = useAuthStore.getState().accessToken;

    // Admin request ke liye admin token use karo. Agar admin session nahi hai
    // (admin ne normal /login se login kiya hai) to user token fallback karo —
    // backend ROLE_ADMIN check karta hai, token ka source matter nahi karta.
    const token = adminRequest ? (adminToken ?? userToken) : userToken;

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

/**
 * Refresh queue — user aur admin ke liye SEPARATE.
 * Pehle ek hi module-level `isRefreshing` flag dono flows ke liye tha, jisse
 * ek flow ka naya token doosre flow ke queued requests par attach ho jata tha.
 */
const userRefreshState = { isRefreshing: false, pending: [] as ((token: string) => void)[] };
const adminRefreshState = { isRefreshing: false, pending: [] as ((token: string) => void)[] };

// Response interceptor — 401 pe token refresh karo
apiClient.interceptors.response.use(
  (response) => response,
  async(error) => {
    const is401 = error.response?.status === 401;
    const originalRequest = error.config;
    const requestUrl = originalRequest?.url || "";

    // Auth endpoints par retry mat karo — infinite loop hoga
    // /auth/me ko retry karne do (wo protected endpoint hai, uska 401 = real session invalid)
    // /auth/refresh aur /auth/login pe retry nahi hoga
    const isRefreshOrLoginEndpoint = requestUrl.includes("/auth/refresh") ||
      requestUrl.includes("/auth/login") ||
      requestUrl.includes("/auth/admin/refresh") ||
      requestUrl.includes("/auth/admin/login");

    if (!error.response || !is401 || originalRequest._retry || isRefreshOrLoginEndpoint) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    const adminRequest = isAdminRequest(originalRequest.url);
    const state = adminRequest ? adminRefreshState : userRefreshState;

    if (state.isRefreshing) {
      return new Promise((resolve, reject) => {
        state.pending.push((newToken:string) => {
          if (!newToken) {
            // Error object bhejo — string nahi, warna UI ke axios.isAxiosError
            // checks fail ho jate the aur generic message dikhta tha
            reject(new Error("Token refresh failed"));
            return;
          }
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          resolve(apiClient(originalRequest));
        });
      });
    }

    state.isRefreshing = true;

    try {
      let newToken: string;

      if (adminRequest) {
        // Admin ka alag refresh endpoint. Agar admin session nahi hai to
        // user session se refresh karke try karo (admin ne normal /login se
        // login kiya ho). Dono fail ho to hi logout.
        try {
          const res = await adminRefreshToken();
          newToken = res.accessToken;
          if (useAdminStore.getState().adminStatus) {
            useAdminStore.setState({
              adminToken: newToken,
              adminUser: res.user,
              adminStatus: true,
            });
          }
        } catch (adminErr) {
          const userSessionActive = useAuthStore.getState().authStatus;
          if (!userSessionActive) {
            throw adminErr;
          }
          const res = await refreshToken();
          newToken = res.accessToken;
          useAuthStore.getState().changeLocalLoginData(res.accessToken, res.user, true);
        }
      } else {
        // User ka refresh endpoint
        const res = await refreshToken();
        newToken = res.accessToken;
        useAuthStore.getState().changeLocalLoginData(res.accessToken, res.user, true);
      }

      if (!newToken) throw new Error("Failed to refresh token");

      // Is scope ke queued requests resolve karo
      state.pending.forEach((callback) => callback(newToken));
      state.pending = [];

      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return apiClient(originalRequest);

    } catch(err) {
      // Is scope ke queued requests reject karo
      state.pending.forEach((callback) => callback(""));
      state.pending = [];

      if (adminRequest) {
        // User session bhi nahi hai to hi logout karo
        if (useAuthStore.getState().authStatus || useAdminStore.getState().adminStatus) {
          useAdminStore.getState().adminLogout();
        }
      } else {
        useAuthStore.getState().logout();
      }
      return Promise.reject(err);
    } finally {
      state.isRefreshing = false;
    }
  }
);

export default apiClient;
