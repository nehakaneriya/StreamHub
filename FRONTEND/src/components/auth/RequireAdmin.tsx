import { useEffect, useRef, useState } from "react";
import { Navigate, Outlet } from "react-router-dom";
import useAdminStore from "@/auth/adminStore";
import { adminRefreshToken } from "@/service/Authservice";

/**
 * Protected admin route ka guard.
 *
 * SECURITY/CORRECTNESS: Pehle guard sirf localStorage me persisted
 * `adminStatus` boolean check karta tha — jo user directly edit kar sakta tha
 * (admin UI shell render ho jata tha). Ab guard:
 *   1. admin session ko refresh cookie se restore karta hai (adminToken memory
 *      me hi rehta hai, localStorage me nahi),
 *   2. verify hone tak content render nahi karta,
 *   3. fail hone par logout karke /admin-login par bhejta hai.
 *
 * Data access hamesha server-side protected hai (SecurityConfig + @PreAuthorize),
 * yeh UI-level defence-in-depth hai.
 */
export default function RequireAdmin() {
  const adminStatus = useAdminStore((state) => state.adminStatus);
  const adminUser = useAdminStore((state) => state.adminUser);
  const adminToken = useAdminStore((state) => state.adminToken);
  const adminLogout = useAdminStore((state) => state.adminLogout);

  // Render ke dauran derive — koi admin session record hi nahi hai
  const hasNoAdminSession = !adminStatus && !adminUser && !adminToken;

  const [verification, setVerification] = useState<"pending" | "ok" | "failed">("pending");
  const hasChecked = useRef(false);

  useEffect(() => {
    if (hasNoAdminSession) return;
    if (hasChecked.current) return;
    hasChecked.current = true;

    const restore = async () => {
      // Token already memory me hai (abhi login kiya) — seedha OK
      if (useAdminStore.getState().adminToken) {
        setVerification("ok");
        return;
      }
      // Page refresh ke baad token gayab ho gaya — cookie se restore karo
      try {
        const res = await adminRefreshToken();
        useAdminStore.setState({
          adminToken: res.accessToken,
          adminUser: res.user,
          adminStatus: true,
        });
        setVerification("ok");
      } catch {
        // adminLogout await karo — warna store clear hone se pehle
        // ek render me adminStatus=true aur adminToken=null ho jata tha
        await useAdminStore.getState().adminLogout();
        setVerification("failed");
      }
    };

    restore();
  }, [hasNoAdminSession, adminLogout]);

  if (hasNoAdminSession) {
    return <Navigate to="/admin-login" replace />;
  }

  if (verification === "pending") {
    return (
      <div className="flex min-h-screen bg-[#0f0f0f] items-center justify-center">
        <div className="w-8 h-8 border-4 border-red-600 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (verification === "failed") {
    return <Navigate to="/admin-login" replace />;
  }

  return <Outlet />;
}
