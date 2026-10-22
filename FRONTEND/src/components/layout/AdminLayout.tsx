import { NavLink, Outlet } from "react-router-dom";
import { useState } from "react";
import { Menu, LayoutDashboard, Users, Video, LogOut, MessageSquare, MessageCircle, X, Tag } from "lucide-react";
import useAdminStore from "@/auth/adminStore";
import LogoutConfirmModal from "@/components/layout/LogoutConfirmModal";

export default function AdminLayout() {

  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [showLogoutModal, setShowLogoutModal] = useState(false);

  // NOTE: Auth guard ab RequireAdmin component me hai (main.tsx me).
  // Yahan duplicate restore/guard logic nahi rakhi jaati.
  const adminLogout = useAdminStore((state) => state.adminLogout);

  const handleLogoutConfirm = async () => {
    setShowLogoutModal(false);
    await adminLogout();
    window.location.href = "/admin-login";
  };

  return (
    <div className="flex min-h-screen bg-[#0f0f0f] text-white">

      {/* Mobile backdrop */}
      {mobileOpen && (
        <div
          onClick={() => setMobileOpen(false)}
          className="fixed inset-0 bg-black/60 z-30 lg:hidden"
        />
      )}

      {/* Sidebar */}
      <aside
        className={`fixed lg:sticky top-0 left-0 h-screen z-40 bg-[#181818] border-r border-gray-800 transition-all duration-300 w-64 ${
          collapsed ? "lg:w-20" : "lg:w-64"
        } ${mobileOpen ? "translate-x-0" : "-translate-x-full"} lg:translate-x-0 overflow-y-auto`}
      >

        <div className="flex items-center justify-between p-4">
          {!collapsed && (
            <h2 className="text-xl font-bold">
              <span className="text-red-600">Admin</span>
            </h2>
          )}
          <div className="flex items-center gap-2">
            <button
              onClick={() => setCollapsed(!collapsed)}
              className="hidden lg:inline-flex text-gray-300 hover:text-white cursor-pointer"
            >
              <Menu />
            </button>
            <button
              onClick={() => setMobileOpen(false)}
              className="lg:hidden text-gray-300 hover:text-white cursor-pointer"
              aria-label="Close menu"
            >
              <X />
            </button>
          </div>
        </div>

        <nav className="mt-2 space-y-2 px-2">

          <NavLink
            to="/admin/dashboard"
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-2 rounded-lg transition ${
                isActive ? "bg-red-600" : "hover:bg-gray-800 text-gray-300"
              }`
            }
          >
            <LayoutDashboard size={20} />
            {!collapsed && "Dashboard"}
          </NavLink>

          <NavLink
            to="/admin/users"
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-2 rounded-lg transition ${
                isActive ? "bg-red-600" : "hover:bg-gray-800 text-gray-300"
              }`
            }
          >
            <Users size={20} />
            {!collapsed && "Users"}
          </NavLink>

          <NavLink
            to="/admin/videos"
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-2 rounded-lg transition ${
                isActive ? "bg-red-600" : "hover:bg-gray-800 text-gray-300"
              }`
            }
          >
            <Video size={20} />
            {!collapsed && "Videos"}
          </NavLink>

          <NavLink
            to="/admin/categories"
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-2 rounded-lg transition ${
                isActive ? "bg-red-600" : "hover:bg-gray-800 text-gray-300"
              }`
            }
          >
            <Tag size={20} />
            {!collapsed && "Categories"}
          </NavLink>

          <NavLink
            to="/admin/feedbacks"
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-2 rounded-lg transition ${
                isActive ? "bg-red-600" : "hover:bg-gray-800 text-gray-300"
              }`
            }
          >
            <MessageSquare size={20} />
            {!collapsed && "Feedback"}
          </NavLink>

          <NavLink
            to="/admin/comments"
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-2 rounded-lg transition ${
                isActive ? "bg-red-600" : "hover:bg-gray-800 text-gray-300"
              }`
            }
          >
            <MessageCircle size={20} />
            {!collapsed && "Comments"}
          </NavLink>

          {/* Logout — modal ke saath */}
          <button
            onClick={() => setShowLogoutModal(true)}
            className="flex items-center gap-3 px-4 py-2 rounded-lg hover:bg-gray-800 text-gray-300 mt-4 w-full cursor-pointer"
          >
            <LogOut size={20} />
            {!collapsed && "Logout"}
          </button>

        </nav>
      </aside>

      {/* Main Content */}
      <main className="flex-1 p-4 sm:p-8 transition-all duration-300 min-w-0">
        {/* Mobile top bar */}
        <button
          onClick={() => setMobileOpen(true)}
          className="lg:hidden mb-4 flex items-center gap-2 px-3 py-2 bg-[#181818] border border-gray-800 rounded-lg text-gray-300 hover:text-white transition cursor-pointer"
        >
          <Menu size={18} />
          <span className="text-sm font-medium">Menu</span>
        </button>
        <Outlet />
      </main>

      {/* Logout Confirm Modal */}
      {showLogoutModal && (
        <LogoutConfirmModal
          onConfirm={handleLogoutConfirm}
          onCancel={() => setShowLogoutModal(false)}
        />
      )}

    </div>
  );
}