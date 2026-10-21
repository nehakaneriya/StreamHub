import { FaGoogle, FaGithub } from "react-icons/fa";

function Oauth2Buttons() {

  const backendUrl =
    import.meta.env.VITE_BACKEND_URL !== undefined && import.meta.env.VITE_BACKEND_URL !== ""
      ? import.meta.env.VITE_BACKEND_URL
      : "";

  return (
    <>
      {/* Divider */}
      <div className="relative my-8">
        <div className="absolute inset-0 flex items-center">
          <div className="w-full border-t border-white/5"></div>
        </div>
        <div className="relative flex justify-center text-xs uppercase">
          <span className="bg-[#121214] px-4 text-gray-500">
            Fast Access
          </span>
        </div>
      </div>

      {/* OAuth Buttons */}
      <div className="flex gap-4">
        <a
          href={`${backendUrl}/oauth2/authorization/google`}
          className="flex-1 flex items-center cursor-pointer justify-center bg-white/5 border border-white/10 py-3 rounded-2xl hover:bg-white/10 transition"
        >
          <FaGoogle size={20} />
        </a>

        {/* Github */}
        <a
          href={`${backendUrl}/oauth2/authorization/github`}
          className="flex-1 flex items-center cursor-pointer justify-center bg-white/5 border border-white/10 py-3 rounded-2xl hover:bg-white/10 transition"
        >
          <FaGithub size={20} />
        </a>
      </div>
    </>
  );
}

export default Oauth2Buttons;
