import { useEffect, useState } from "react";
import { Mail, ShieldCheck, RefreshCw, X } from "lucide-react";
import { verifyOtp, resendOtp, cancelRegistration } from "@/service/Authservice";
import { toast } from "react-toastify";
import { Spinner } from "@/components/ui/spinner";
import axios from "axios";

const OTP_TTL_SECONDS = 300; // OTP 5 min valid (backend `app.otp.ttl-seconds`)

interface OtpModalProps {
  email: string;
  onSuccess: () => void;
  onClose: () => void;
}

export default function OtpModal({ email, onSuccess, onClose }: OtpModalProps) {
  const [otp, setOtp] = useState("");
  const [digits, setDigits] = useState<string[]>(Array(6).fill(""));
  const [loading, setLoading] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [expiry, setExpiry] = useState(OTP_TTL_SECONDS);

  // ── Background lock: scroll block + browser Back trap ─────────
  useEffect(() => {
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    // Browser Back dabane par bhi yahin raho — modal se bahar jana possible hi nahi
    const url = window.location.href;
    window.history.pushState(null, "", url);
    const handlePopState = () => window.history.pushState(null, "", url);
    window.addEventListener("popstate", handlePopState);

    return () => {
      document.body.style.overflow = prevOverflow;
      window.removeEventListener("popstate", handlePopState);
    };
  }, []);

  // ── OTP expiry countdown (5 min) ─────────────────────────────
  useEffect(() => {
    if (expiry <= 0) return;
    const t = setTimeout(() => setExpiry((e) => e - 1), 1000);
    return () => clearTimeout(t);
  }, [expiry]);

  const expired = expiry <= 0;

  // Resend cooldown (60s)
  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  const formatTime = (s: number) => {
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m}:${sec.toString().padStart(2, "0")}`;
  };

  const handleDigit = (index: number, value: string) => {
    const v = value.replace(/\D/g, "").slice(-1);
    const next = [...digits];
    next[index] = v;
    setDigits(next);
    setOtp(next.join(""));
    if (v && index < 5) {
      document.getElementById(`otp-${index + 1}`)?.focus();
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Backspace" && !digits[index] && index > 0) {
      document.getElementById(`otp-${index - 1}`)?.focus();
    }
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
    const next = Array(6).fill("");
    pasted.split("").forEach((ch, i) => (next[i] = ch));
    setDigits(next);
    setOtp(next.join(""));
  };

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    if (expired) {
      toast.error("OTP has expired. Please resend a new one ⏰");
      return;
    }
    if (otp.length !== 6) {
      toast.error("Please enter the 6-digit OTP ❌");
      return;
    }
    try {
      setLoading(true);
      await verifyOtp(email, otp);
      toast.success("Email verified successfully 🎉");
      onSuccess();
    } catch (error) {
      const message =
        axios.isAxiosError(error) && error.response?.data?.message
          ? error.response.data.message
          : "Verification failed ❌";
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (cooldown > 0) return;
    try {
      await resendOtp(email);
      toast.success("New OTP sent 📧");
      setCooldown(60);
      setExpiry(OTP_TTL_SECONDS); // naya OTP → phir se 5 min fresh
    } catch (error) {
      const message =
        axios.isAxiosError(error) && error.response?.data?.message
          ? error.response.data.message
          : "Could not resend OTP ❌";
      toast.error(message);
    }
  };

  const handleCancel = async () => {
    if (cancelling || loading) return;
    // SECURITY: Backend cancellation ke liye OTP maangta hai (ownership proof).
    // Bina OTP ke account delete nahi hoga — user ko pehle OTP enter karna hoga.
    if (otp.length !== 6) {
      toast.error("Enter the 6-digit OTP from your email to cancel registration ❌");
      return;
    }
    try {
      setCancelling(true);
      if (email) {
        await cancelRegistration(email, otp);
      }
      toast.info("Registration cancelled");
    } catch (error) {
      const message =
        axios.isAxiosError(error) && error.response?.data?.message
          ? error.response.data.message
          : "Could not cancel registration ❌";
      toast.error(message);
      return;
    } finally {
      setCancelling(false);
      onClose();
    }
  };

  const inputBase =
    "w-full h-14 text-center text-2xl font-black bg-white/5 border border-white/10 rounded-xl focus:ring-2 focus:ring-red-600/50 focus:border-red-600 outline-none transition-all text-white";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm px-4">
      {/* Card — backdrop pe click kuch nahi karta, modal forced hai */}
      <div
        className="w-full max-w-md bg-[#121214] border border-white/10 rounded-3xl shadow-2xl p-5 sm:p-6 animate-fade-in-up"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex justify-end -mb-2">
          {/* X dabane par registration cancel aur modal band */}
          <button
            type="button"
            onClick={handleCancel}
            disabled={cancelling}
            aria-label="Close"
            className="text-gray-600 hover:text-white transition-colors cursor-pointer disabled:opacity-50"
          >
            <X size={16} />
          </button>
        </div>

        <div className="text-center mb-4">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-red-600/15 border border-red-600/20 mb-3">
            <ShieldCheck size={26} className="text-red-500" />
          </div>
          <h1 className="text-xl font-black tracking-tighter">
            Verify your <span className="text-red-600">Email</span>
          </h1>
          <p className="text-gray-500 text-xs mt-1">
            Enter the 6-digit code we sent to
          </p>
          <p className="flex items-center justify-center gap-1.5 text-gray-300 text-sm mt-1 font-medium break-all">
            <Mail size={14} className="text-red-500 shrink-0" /> {email}
          </p>

          {/* OTP expiry countdown */}
          <div className="mt-3 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/5 border border-white/10 text-xs font-semibold">
            <span className={expired ? "text-red-500" : "text-gray-400"}>
              {expired ? "OTP expired" : "OTP expires in"}
            </span>
            <span className={`tabular-nums font-black ${expired ? "text-red-500" : "text-white"}`}>
              {expired ? "0:00" : formatTime(expiry)}
            </span>
          </div>
        </div>

        <form className="space-y-4" onSubmit={handleVerify}>
          <div className="flex justify-center gap-2">
            {digits.map((d, i) => (
              <input
                key={i}
                id={`otp-${i}`}
                type="text"
                inputMode="numeric"
                autoFocus={i === 0}
                value={d}
                onChange={(e) => handleDigit(i, e.target.value)}
                onKeyDown={(e) => handleKeyDown(i, e)}
                onPaste={handlePaste}
                maxLength={1}
                className={`${inputBase} w-12 sm:w-14`}
              />
            ))}
          </div>

          <button
            type="submit"
            disabled={loading || expired || cancelling}
            className="w-full bg-red-600 cursor-pointer hover:bg-red-700 py-3 rounded-xl text-base font-bold transition-all shadow-lg shadow-red-600/30 active:scale-[0.98] disabled:opacity-70 flex items-center justify-center gap-2"
          >
            {loading ? <Spinner /> : expired ? "OTP Expired — Resend" : <>Verify &amp; Continue</>}
          </button>
        </form>

        <div className="flex items-center justify-between gap-2 mt-4 pt-3 border-t border-white/5">
          <button
            type="button"
            onClick={handleCancel}
            disabled={cancelling || loading}
            className="text-xs text-gray-500 hover:text-red-400 transition-colors cursor-pointer disabled:opacity-50"
          >
            {cancelling ? "Cancelling..." : "Cancel Registration"}
          </button>

          <button
            type="button"
            onClick={handleResend}
            disabled={cooldown > 0 || cancelling}
            className="flex items-center gap-1.5 text-xs text-gray-400 hover:text-white transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <RefreshCw size={13} className={cooldown > 0 ? "animate-spin" : ""} />
            {cooldown > 0 ? `Resend in ${cooldown}s` : "Resend OTP"}
          </button>
        </div>
      </div>
    </div>
  );
}