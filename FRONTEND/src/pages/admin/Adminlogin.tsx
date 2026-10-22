import { useState } from "react";
import { useNavigate } from "react-router-dom";
import useAdminStore from "@/auth/adminStore";

export default function Adminlogin() {

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const adminLogin = useAdminStore((state) => state.adminLogin);
  const adminLoading = useAdminStore((state) => state.adminLoading);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    try {
      await adminLogin(email, password);
      navigate("/admin/dashboard");
    } catch (err) {
      // Token/headers nahi log karte — AxiosError me config.headers.Authorization hota hai
      console.error("Admin login failed:", err instanceof Error ? err.message : "Unknown error");
      // NOTE: Backend ab "not an admin" aur "wrong password" ke liye SAME message
      // deta hai (user enumeration avoid karne ke liye) — isliye yahan bhi
      // ek hi generic message dikhana sahi hai.
      setError("Invalid admin credentials");
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-[#0f0f0f] text-white px-4">
      <div className="bg-[#181818] p-8 rounded-xl w-full max-w-md border border-gray-800">
        <h2 className="text-2xl font-bold mb-6 text-center">
          <span className="text-red-600">Admin</span> Login
        </h2>
        <form onSubmit={handleLogin} className="space-y-4">
          <input
            type="email"
            placeholder="Admin Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full p-3 rounded-lg bg-[#0f0f0f] border border-gray-700 focus:outline-none focus:border-red-600 transition-colors"
          />
          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full p-3 rounded-lg bg-[#0f0f0f] border border-gray-700 focus:outline-none focus:border-red-600 transition-colors"
          />
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <button
            type="submit"
            disabled={adminLoading}
            className="w-full bg-red-600 hover:bg-red-700 p-3 rounded-lg font-semibold disabled:opacity-60 cursor-pointer transition"
          >
            {adminLoading ? "Logging in..." : "Login"}
          </button>
        </form>
      </div>
    </div>
  );
}