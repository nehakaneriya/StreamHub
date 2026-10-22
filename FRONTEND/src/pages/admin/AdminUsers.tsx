import { useEffect, useState } from "react";
import { getAllUsers, makeAdmin, removeAdmin, deleteUser } from "../../service/Adminservice";
import type UserT from "../../models/User";
import { Trash2, ShieldCheck, ShieldOff, User } from "lucide-react";
import { toast } from "react-toastify";
import useAdminStore from "@/auth/adminStore";
import axios from "axios";

export default function Users() {

  const [users, setUsers] = useState<UserT[]>([]);
  const [loading, setLoading] = useState(true);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [makingAdminId, setMakingAdminId] = useState<string | null>(null);
  const [removingAdminId, setRemovingAdminId] = useState<string | null>(null);

  const adminUser = useAdminStore((state) => state.adminUser);

  const loadUsers = () => {
    setLoading(true);
    getAllUsers()
      .then(setUsers)
      .catch(console.error)
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const handleMakeAdmin = async (id: string) => {
    if (!confirm("Are you sure you want to make this user an ADMIN? They will get full access to the admin panel.")) return;
    setMakingAdminId(id);
    try {
      await makeAdmin(id);
      toast.success("Admin role assigned successfully");
      loadUsers();
    } catch (err) {
      console.error(err);
      toast.error("Failed to assign admin role");
    } finally {
      setMakingAdminId(null);
    }
  };

  const handleRemoveAdmin = async (id: string) => {
    if (adminUser?.id === id) {
      toast.error("You cannot remove your own admin role!");
      return;
    }
    if (!confirm("Are you sure you want to remove ADMIN role from this user? They will become a normal user.")) return;
    setRemovingAdminId(id);
    try {
      await removeAdmin(id);
      toast.success("Admin role removed successfully");
      loadUsers();
    } catch (err) {
      const message =
        axios.isAxiosError(err) && err.response?.data?.message
          ? err.response.data.message
          : "Failed to remove admin role";
      toast.error(message);
      console.error(err);
    } finally {
      setRemovingAdminId(null);
    }
  };

  const handleDelete = async (userId: string) => {
    // Apne aap ko delete karne se roko
    if (adminUser?.id === userId) {
      toast.error("You cannot delete your own account!");
      return;
    }

    if (!confirm("Are you sure you want to delete this user?")) return;
    setDeletingId(userId);
    try {
      await deleteUser(userId);
      toast.success("User deleted successfully");
      loadUsers();
    } catch (err) {
      const message =
        axios.isAxiosError(err) && err.response?.data?.message
          ? err.response.data.message
          : "Failed to delete user";
      toast.error(message);
      console.error(err);
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="p-2">
      <h1 className="text-3xl font-bold mb-6">
        <span className="text-red-600">Manage</span> Users
      </h1>

      {/* Stats Bar */}
      <div className="flex flex-wrap gap-4 mb-6">
        <div className="bg-[#181818] border border-gray-800 rounded-lg px-4 py-2 flex items-center gap-2">
          <User size={16} className="text-red-600" />
          <span className="text-gray-400 text-sm">Total:</span>
          <span className="font-bold">{users.length}</span>
        </div>
        <div className="bg-[#181818] border border-gray-800 rounded-lg px-4 py-2 flex items-center gap-2">
          <ShieldCheck size={16} className="text-red-600" />
          <span className="text-gray-400 text-sm">Admins:</span>
          <span className="font-bold">
            {users.filter(u => (u.roles ?? []).some(r => r.name === "ROLE_ADMIN")).length}
          </span>
        </div>
        <div className="bg-[#181818] border border-yellow-700/40 rounded-lg px-4 py-2 flex items-center gap-2">
          <ShieldCheck size={16} className="text-yellow-500" />
          <span className="text-gray-400 text-sm">Dual-Role:</span>
          <span className="font-bold text-yellow-500">
            {users.filter(u => (u.roles ?? []).length > 1).length}
          </span>
        </div>
      </div>

      {/* Loading */}
      {loading ? (
        <div className="text-center py-20 text-gray-500">Loading users...</div>
      ) : (
        <div className="bg-[#181818] border border-gray-800 rounded-xl overflow-x-auto">
          <table className="w-full text-left min-w-[800px]">
            <thead className="bg-[#111] border-b border-gray-800">
              <tr>
                <th className="p-4 text-gray-400 text-sm">Name</th>
                <th className="p-4 text-gray-400 text-sm">Email</th>
                <th className="p-4 text-gray-400 text-sm">Provider</th>
                <th className="p-4 text-gray-400 text-sm">Roles</th>
                <th className="p-4 text-gray-400 text-sm">Status</th>
                <th className="p-4 text-gray-400 text-sm">Actions</th>
              </tr>
            </thead>

            <tbody>
              {users.map(user => {
                // roles hamesha present nahi hota (backend Set empty/null de sakta hai)
                const userRoles = user.roles ?? [];
                const isDualRole = userRoles.length > 1;
                const isAdmin = userRoles.some(r => r.name === "ROLE_ADMIN");
                return (
                <tr
                  key={user.id}
                  className={`border-b border-gray-800 hover:bg-[#1f1f1f] transition ${
                    isDualRole ? "bg-yellow-900/10 border-l-2 border-l-yellow-600" : ""
                  }`}
                  title={isDualRole ? "This user has multiple roles" : undefined}
                >
                  {/* Name */}
                  <td className="p-4">
                    <div className="flex items-center gap-2">
                      <div className="w-8 h-8 rounded-full bg-red-600/20 flex items-center justify-center text-red-500 font-bold text-sm">
                        {user.name?.charAt(0).toUpperCase() || "?"}
                      </div>
                      <span>{user.name}</span>
                    </div>
                  </td>

                  {/* Email */}
                  <td className="p-4 text-gray-300">{user.email}</td>

                  {/* Provider */}
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${
                      user.provider === "GOOGLE"
                        ? "bg-blue-600/20 text-blue-400"
                        : user.provider === "GITHUB"
                        ? "bg-purple-600/20 text-purple-400"
                        : "bg-gray-700 text-gray-300"
                    }`}>
                      {user.provider || "LOCAL"}
                    </span>
                  </td>

                  {/* Roles */}
                  <td className="p-4">
                    <div className="flex flex-wrap gap-1">
                      {userRoles.map((role) => (
                        <span
                          key={role.name}
                          className={`px-2 py-1 rounded-full text-xs font-semibold ${
                            role.name === "ROLE_ADMIN"
                              ? "bg-red-600/20 text-red-400"
                              : "bg-gray-700 text-gray-300"
                          }`}
                        >
                          {role.name.replace("ROLE_", "")}
                        </span>
                      ))}
                    </div>
                  </td>

                  {/* Status */}
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded-full text-xs font-semibold ${
                      user.enable
                        ? "bg-green-600/20 text-green-400"
                        : "bg-red-600/20 text-red-400"
                    }`}>
                      {user.enable ? "Active" : "Disabled"}
                    </span>
                  </td>

                  {/* Actions */}
                  <td className="p-4">
                    <div className="flex gap-2">
                      <button
                        onClick={() => handleMakeAdmin(user.id)}
                        disabled={
                          makingAdminId === user.id ||
                          isAdmin
                        }
                        className="flex items-center gap-1 px-3 py-1 bg-red-600 rounded hover:bg-red-500 text-sm disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer transition"
                      >
                        <ShieldCheck size={14} />
                        {makingAdminId === user.id ? "..." : "Make Admin"}
                      </button>

                      {isAdmin && (
                        <button
                          onClick={() => handleRemoveAdmin(user.id)}
                          disabled={removingAdminId === user.id || adminUser?.id === user.id}
                          title={adminUser?.id === user.id ? "You cannot remove your own admin role" : undefined}
                          className="flex items-center gap-1 px-3 py-1 bg-yellow-700 rounded hover:bg-yellow-600 text-sm disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer transition"
                        >
                          <ShieldOff size={14} />
                          {removingAdminId === user.id ? "..." : "Remove Admin"}
                        </button>
                      )}

                      <button
                        onClick={() => handleDelete(user.id)}
                        disabled={deletingId === user.id}
                        className="flex items-center gap-1 px-3 py-1 bg-gray-700 rounded hover:bg-gray-600 text-sm disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer transition"
                      >
                        <Trash2 size={14} />
                        {deletingId === user.id ? "..." : "Delete"}
                      </button>
                    </div>
                  </td>

                </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
