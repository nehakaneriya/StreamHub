import { useEffect, useState } from "react";
import useAuthStore from "@/auth/store";
import { getMyVideos } from "@/service/VideoService";
import { updateUserProfile } from "@/service/Authservice";
import { toast } from "react-toastify";
import { User, Mail, Shield, Calendar, Pencil, X, Eye, EyeOff, Loader2 } from "lucide-react";
import axios from "axios";

export default function UserProfile() {
  const user = useAuthStore((state) => state.user);
  const changeLocalLoginData = useAuthStore((state) => state.changeLocalLoginData);
  const accessToken = useAuthStore((state) => state.accessToken);

  const [videoCount, setVideoCount] = useState<number>(0);

  // Modal state
  const [showModal, setShowModal] = useState(false);
  const [saving, setSaving] = useState(false);

  // Form fields
  const [newName, setNewName] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [showCurrent, setShowCurrent] = useState(false);

  // OAuth2 user hai ya LOCAL?
  const isOAuthUser = user?.provider && user.provider.toUpperCase() !== "LOCAL";

  useEffect(() => {
    const fetchVideos = async () => {
      try {
        const videos = await getMyVideos();
        setVideoCount(videos.length);
      } catch {
        console.log("Failed to fetch videos");
      }
    };
    fetchVideos();
  }, []);

  // Modal khulne pe current name pre-fill karo
  const openModal = () => {
    setNewName(user?.name || "");
    setNewPassword("");
    setConfirmPassword("");
    setCurrentPassword("");
    setShowPassword(false);
    setShowConfirm(false);
    setShowCurrent(false);
    setShowModal(true);
  };

  const closeModal = () => {
    if (saving) return; // saving ke dauran close mat karo
    setShowModal(false);
  };

  const handleSave = async () => {
    // Validations
    const trimmedName = newName.trim();
    if (!trimmedName) {
      toast.error("Name cannot be empty");
      return;
    }

    if (!isOAuthUser && newPassword) {
      // SECURITY: Current password zaroori hai (backend verify karta hai)
      if (!currentPassword) {
        toast.error("Please enter your current password to change it");
        return;
      }
      if (newPassword.length < 6) {
        toast.error("Password must be at least 6 characters");
        return;
      }
      if (!/[A-Za-z]/.test(newPassword)) {
        toast.error("Password must contain at least one letter");
        return;
      }
      if (!/\d/.test(newPassword)) {
        toast.error("Password must contain at least one number");
        return;
      }
      if (!/[^A-Za-z0-9]/.test(newPassword)) {
        toast.error("Password must contain at least one special character");
        return;
      }
      if (newPassword !== confirmPassword) {
        toast.error("Passwords do not match");
        return;
      }
      if (newPassword === currentPassword) {
        toast.error("New password must be different from the current password");
        return;
      }
    }

    // Kuch change hua?
    const nameChanged = trimmedName !== user?.name;
    const passwordChanged = !isOAuthUser && newPassword.length > 0;

    if (!nameChanged && !passwordChanged) {
      toast.info("No changes to save");
      closeModal();
      return;
    }

    try {
      setSaving(true);

      const payload: { name?: string; password?: string; currentPassword?: string } = {};
      if (nameChanged) payload.name = trimmedName;
      if (passwordChanged) {
        payload.password = newPassword;
        payload.currentPassword = currentPassword;
      }

      const updatedUser = await updateUserProfile(user!.id, payload);

      // Zustand store mein updated user save karo
      changeLocalLoginData(accessToken!, updatedUser, true);

      toast.success("Profile updated successfully!");
      closeModal();
    } catch (error) {
      const message =
        axios.isAxiosError(error) && error.response?.data?.message
          ? error.response.data.message
          : "Failed to update profile. Please try again.";
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  if (!user) return null;

  const formattedDate = (dateStr?: string) => {
    if (!dateStr) return "Not Available";
    return new Date(dateStr).toLocaleDateString("en-IN", {
      day: "numeric", month: "long", year: "numeric",
    });
  };

  return (
    <div className="min-h-screen bg-[#0f0f0f] text-white flex justify-center px-4 sm:px-6 py-8 sm:py-16">
      <div className="w-full max-w-4xl">

        {/* ── Top Card ── */}
        <div className="bg-[#181818] rounded-2xl p-6 sm:p-8 border border-gray-800 shadow-xl">
          <div className="flex flex-col sm:flex-row items-center gap-4 sm:gap-8 text-center sm:text-left">

            {/* Avatar */}
            <div className="w-20 h-20 sm:w-28 sm:h-28 rounded-full bg-red-600 flex items-center justify-center text-3xl sm:text-4xl font-bold shrink-0 select-none">
              {user?.name?.charAt(0).toUpperCase()}
            </div>

            <div className="flex-1 min-w-0">
              <h2 className="text-2xl sm:text-3xl font-bold truncate">{user?.name}</h2>
              <p className="text-gray-400 mt-1 truncate">{user?.email}</p>

              <div className="flex items-center gap-3 mt-4 flex-wrap justify-center sm:justify-start">
                {/* Provider badge */}
                <span className={`px-3 py-1 rounded-full text-xs font-semibold ${
                  isOAuthUser
                    ? "bg-blue-600/20 text-blue-400 border border-blue-600/40"
                    : "bg-gray-700 text-gray-300 border border-gray-600"
                }`}>
                  {user?.provider?.toUpperCase()}
                </span>

                {/* Role badge */}
                <span className="px-3 py-1 rounded-full text-xs font-semibold bg-red-600/20 text-red-400 border border-red-600/40">
                  {user?.roles?.[0]?.name?.replace("ROLE_", "") || "USER"}
                </span>

                {/* Edit button */}
                <button
                  onClick={openModal}
                  className="flex items-center gap-2 px-5 py-2 bg-red-600 rounded-full hover:bg-red-500 transition text-sm font-medium cursor-pointer"
                >
                  <Pencil size={14} />
                  Edit Profile
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* ── Bottom Cards ── */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-8">

          {/* Account Info */}
          <div className="bg-[#181818] p-6 rounded-xl border border-gray-800">
            <h3 className="text-lg font-semibold mb-4 border-b border-gray-700 pb-2 flex items-center gap-2">
              <User size={16} className="text-red-500" />
              Account Information
            </h3>
            <div className="space-y-3 text-gray-300 text-sm">
              <div className="flex items-start gap-2">
                <User size={14} className="text-gray-500 mt-0.5 shrink-0" />
                <div>
                  <span className="text-gray-500 block text-xs">Name</span>
                  {user?.name}
                </div>
              </div>
              <div className="flex items-start gap-2">
                <Mail size={14} className="text-gray-500 mt-0.5 shrink-0" />
                <div>
                  <span className="text-gray-500 block text-xs">Email</span>
                  {user?.email}
                </div>
              </div>
              <div className="flex items-start gap-2">
                <Shield size={14} className="text-gray-500 mt-0.5 shrink-0" />
                <div>
                  <span className="text-gray-500 block text-xs">Role</span>
                  {user?.roles?.[0]?.name?.replace("ROLE_", "") || "USER"}
                </div>
              </div>
              <div className="flex items-start gap-2">
                <Calendar size={14} className="text-gray-500 mt-0.5 shrink-0" />
                <div>
                  <span className="text-gray-500 block text-xs">Member Since</span>
                  {formattedDate(user?.createdAt)}
                </div>
              </div>
              <div className="flex items-start gap-2">
                <Calendar size={14} className="text-gray-500 mt-0.5 shrink-0" />
                <div>
                  <span className="text-gray-500 block text-xs">Last Updated</span>
                  {formattedDate(user?.updatedAt)}
                </div>
              </div>
            </div>
          </div>

          {/* Activity */}
          <div className="bg-[#181818] p-6 rounded-xl border border-gray-800">
            <h3 className="text-lg font-semibold mb-4 border-b border-gray-700 pb-2">
              Activity
            </h3>
            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <span className="text-gray-400 text-sm">Uploaded Videos</span>
                <span className="text-2xl font-bold text-red-500">{videoCount}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-gray-400 text-sm">Watch History</span>
                <span className="text-2xl font-bold text-gray-600">—</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-gray-400 text-sm">Playlists</span>
                <span className="text-2xl font-bold text-gray-600">—</span>
              </div>
            </div>
          </div>

        </div>
      </div>

      {/* ═══════════════════════════════════════
          EDIT PROFILE MODAL
      ═══════════════════════════════════════ */}
      {showModal && (
        <div
          className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 px-4"
          onClick={(e) => { if (e.target === e.currentTarget) closeModal(); }}
        >
          <div className="bg-[#181818] border border-gray-700 rounded-2xl shadow-2xl w-full max-w-md p-8 relative">

            {/* Close button */}
            <button
              onClick={closeModal}
              disabled={saving}
              className="absolute top-4 right-4 text-gray-500 hover:text-white transition disabled:opacity-40 cursor-pointer"
            >
              <X size={20} />
            </button>

            <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
              <Pencil size={18} className="text-red-500" />
              Edit Profile
            </h2>

            <div className="space-y-5">

              {/* Name field */}
              <div>
                <label className="text-sm font-medium text-gray-400 block mb-1">
                  Full Name
                </label>
                <input
                  type="text"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  disabled={saving}
                  className="w-full p-3 bg-[#0f0f0f] border border-gray-700 rounded-xl text-white focus:outline-none focus:border-red-600 focus:ring-1 focus:ring-red-600 transition disabled:opacity-50"
                  placeholder="Enter your name"
                />
              </div>

              {/* Password fields — sirf LOCAL users ke liye */}
              {!isOAuthUser && (
                <>
                  <div className="border-t border-gray-700 pt-4">
                    <p className="text-xs text-gray-500 mb-3">
                      Leave the password fields blank if you don't want to change your password.
                    </p>

                    {/* Current Password — sirf tab dikhega jab naya password daala ho */}
                    {newPassword && (
                      <div className="mb-4">
                        <label className="text-sm font-medium text-gray-400 block mb-1">
                          Current Password
                        </label>
                        <div className="relative">
                          <input
                            type={showCurrent ? "text" : "password"}
                            value={currentPassword}
                            onChange={(e) => setCurrentPassword(e.target.value)}
                            disabled={saving}
                            className="w-full p-3 pr-10 bg-[#0f0f0f] border border-gray-700 rounded-xl text-white focus:outline-none focus:border-red-600 focus:ring-1 focus:ring-red-600 transition disabled:opacity-50"
                            placeholder="Your current password"
                          />
                          <button
                            type="button"
                            onClick={() => setShowCurrent(!showCurrent)}
                            className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-white cursor-pointer"
                          >
                            {showCurrent ? <EyeOff size={16} /> : <Eye size={16} />}
                          </button>
                        </div>
                      </div>
                    )}

                    {/* New Password */}
                    <div className="mb-4">
                      <label className="text-sm font-medium text-gray-400 block mb-1">
                        New Password
                      </label>
                      <div className="relative">
                        <input
                          type={showPassword ? "text" : "password"}
                          value={newPassword}
                          onChange={(e) => setNewPassword(e.target.value)}
                          disabled={saving}
                          className="w-full p-3 pr-10 bg-[#0f0f0f] border border-gray-700 rounded-xl text-white focus:outline-none focus:border-red-600 focus:ring-1 focus:ring-red-600 transition disabled:opacity-50"
                          placeholder="New password (min 6 chars)"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(!showPassword)}
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-white cursor-pointer"
                        >
                          {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                        </button>
                      </div>
                    </div>

                    {/* Confirm Password */}
                    {newPassword && (
                      <div>
                        <label className="text-sm font-medium text-gray-400 block mb-1">
                          Confirm Password
                        </label>
                        <div className="relative">
                          <input
                            type={showConfirm ? "text" : "password"}
                            value={confirmPassword}
                            onChange={(e) => setConfirmPassword(e.target.value)}
                            disabled={saving}
                            className={`w-full p-3 pr-10 bg-[#0f0f0f] border rounded-xl text-white focus:outline-none focus:ring-1 transition disabled:opacity-50 ${
                              confirmPassword && confirmPassword !== newPassword
                                ? "border-red-500 focus:border-red-500 focus:ring-red-500"
                                : "border-gray-700 focus:border-red-600 focus:ring-red-600"
                            }`}
                            placeholder="Confirm new password"
                          />
                          <button
                            type="button"
                            onClick={() => setShowConfirm(!showConfirm)}
                            className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-white cursor-pointer"
                          >
                            {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                          </button>
                        </div>
                        {confirmPassword && confirmPassword !== newPassword && (
                          <p className="text-red-500 text-xs mt-1">Passwords do not match</p>
                        )}
                      </div>
                    )}
                  </div>
                </>
              )}

              {/* OAuth user note */}
              {isOAuthUser && (
                <div className="bg-blue-600/10 border border-blue-600/30 rounded-xl p-3 text-xs text-blue-400">
                  You are signed in with <strong>{user?.provider}</strong>. Password change is not available for OAuth accounts.
                </div>
              )}

              {/* Action buttons */}
              <div className="flex gap-3 pt-2">
                <button
                  onClick={closeModal}
                  disabled={saving}
                  className="flex-1 py-3 rounded-xl border border-gray-600 text-gray-300 hover:bg-gray-700 transition font-medium disabled:opacity-40 cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSave}
                  disabled={saving}
                  className="flex-1 py-3 rounded-xl bg-red-600 hover:bg-red-500 transition font-medium disabled:opacity-60 flex items-center justify-center gap-2 cursor-pointer"
                >
                  {saving ? (
                    <>
                      <Loader2 size={16} className="animate-spin" />
                      Saving...
                    </>
                  ) : (
                    "Save Changes"
                  )}
                </button>
              </div>

            </div>
          </div>
        </div>
      )}
    </div>
  );
}
