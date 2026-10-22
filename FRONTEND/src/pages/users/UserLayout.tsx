import { Outlet } from "react-router-dom";

/**
 * User pages ka layout.
 *
 * NOTE: Auth guard ab RequireAuth component me hai (main.tsx me route group
 * ke bahar) — yahan duplicate guard logic nahi rakhi jaati, warna do jagah
 * diverge kar sakti hai.
 */
export default function UserLayout() {
  return (
    <div>
      <Outlet />
    </div>
  );
}
