import { useEffect, useRef, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import useAuthStore from "@/auth/store";
import { verifySession } from "@/service/Authservice";

/**
 * Protected user route ka guard.
 *
 * SECURITY/CORRECTNESS: Pehle guard sirf localStorage me persisted
 * `authStatus` boolean check karta tha — jo user directly edit kar sakta tha.
 * Ab guard backend se session verify karta hai aur verify hone tak content
 * render nahi karta. Data access to hamesha server-side protected hai, yeh
 * UI-level defence-in-depth hai.
 */
export default function RequireAuth() {
  const authStatus = useAuthStore((state) => state.authStatus);
  const accessToken = useAuthStore((state) => state.accessToken);
  const logout = useAuthStore((state) => state.logout);
  const location = useLocation();

  // Render ke dauran derive hota hai — iske liye state ki zaroorat nahi
  const isUnauthenticated = !authStatus || !accessToken;

  // Sirf async verification ka result track karte hain
  const [verification, setVerification] = useState<"pending" | "ok" | "failed">("pending");
  const hasChecked = useRef(false);

  useEffect(() => {
    if (isUnauthenticated) return;
    if (hasChecked.current) return;
    hasChecked.current = true;

    verifySession()
      .then(() => setVerification("ok"))
      .catch(() => {
        // 401 — user deleted ya session invalid
        setVerification("failed");
        logout();
      });
  }, [isUnauthenticated, logout]);

  if (isUnauthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (verification === "pending") {
    return (
      <div className="min-h-screen bg-[#0f0f0f] flex items-center justify-center">
        <div className="w-8 h-8 border-4 border-red-600 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (verification === "failed") {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}
