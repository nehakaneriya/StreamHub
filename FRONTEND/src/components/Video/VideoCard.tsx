import { useNavigate } from "react-router-dom";
import { Play, Trash2, Calendar, Eye, Pencil } from "lucide-react";
import { getThumbnailUrl } from "@/service/VideoService";

interface Props {
  videoId: string;
  title: string;
  description: string;
  userId?: string;
  userName?: string;
  contentType?: string;
  category?: string;
  createdAt?: string;
  viewCount?: number;
  userEmailHidden?: boolean;
  onDelete?: (id: string) => void;
  onEdit?: (id: string) => void;
}

export default function VideoCard({
  videoId,
  title,
  description,
  userId,
  userName,
  contentType,
  category,
  createdAt,
  viewCount,
  onDelete,
  onEdit,
}: Props) {
  const navigate = useNavigate();

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return null;
    return new Date(dateStr).toLocaleDateString("en-IN", {
      day: "numeric",
      month: "short",
      year: "numeric",
    });
  };

  const getInitials = (name?: string) => {
    if (!name) return "?";
    return name.split(" ").map((w) => w[0]).join("").toUpperCase().slice(0, 2);
  };

  return (
    <div
      className="bg-[#111111] rounded-xl overflow-hidden border border-gray-800/60 hover:border-red-600/40 transition-all duration-300 group hover:shadow-xl hover:shadow-red-600/5 hover:-translate-y-1 cursor-pointer"
      onClick={() => navigate(`/watch/${videoId}`)}
    >

      {/* Thumbnail — server-generated first frame */}
      <div className="relative aspect-video bg-black overflow-hidden">
        <img
          src={getThumbnailUrl(videoId)}
          alt={title}
          className="w-full h-full object-cover"
          loading="lazy"
          onError={(e) => {
            // Thumbnail na ho to blank black box dikhe (HLS load karne ki zaroorat nahi)
            e.currentTarget.style.visibility = "hidden";
          }}
        />

        {/* Play overlay */}
        <div className="absolute inset-0 bg-black/30 group-hover:bg-black/10 transition-all flex items-center justify-center">
          <div className="w-12 h-12 rounded-full bg-black/50 border-2 border-white/30 flex items-center justify-center group-hover:bg-red-600 group-hover:border-red-600 group-hover:scale-110 transition-all duration-300">
            <Play size={18} className="text-white ml-1" fill="white" />
          </div>
        </div>

        {/* Category badge */}
        {category && (
          <span className="absolute top-2 left-2 bg-red-600/90 text-white text-[10px] font-semibold px-2 py-0.5 rounded-full uppercase tracking-wide">
            {category}
          </span>
        )}

        {/* Content type badge */}
        {contentType && (
          <span className="absolute top-2 right-2 bg-black/70 text-gray-300 text-[10px] font-semibold px-2 py-0.5 rounded-full uppercase tracking-wide">
            {contentType.split("/")[1] || "video"}
          </span>
        )}
      </div>

      {/* Info */}
      <div className="p-3">

        {/* Uploader + Title */}
        <div className="flex gap-2 mb-1">
          <div
            className="w-8 h-8 rounded-full bg-red-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0 mt-0.5 cursor-pointer"
            onClick={(e) => {
              e.stopPropagation();
              if (userId) navigate(`/channel/${userId}`);
            }}
            title={userName}
          >
            {getInitials(userName)}
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-white font-semibold text-sm leading-snug line-clamp-2 group-hover:text-red-400 transition-colors" title={description}>
              {title || "Untitled Video"}
            </h3>
            <button
              className="text-gray-500 text-xs mt-0.5 hover:text-red-500 transition-colors text-left"
              onClick={(e) => {
                e.stopPropagation();
                if (userId) navigate(`/channel/${userId}`);
              }}
              title={`View ${userName || "user"}'s channel`}
            >
              {userName || "Unknown"}
            </button>
          </div>
        </div>

        {/* Date + Views */}
        {createdAt && (
          <div className="flex items-center gap-1 text-gray-600 text-[11px] ml-10">
            <Calendar size={11} />
            <span>{formatDate(createdAt)}</span>
            <span className="mx-1 text-gray-700">•</span>
            <Eye size={11} className="text-red-600/70" />
            <span>{viewCount?.toLocaleString()}</span>
          </div>
        )}

        {/* Edit / Delete buttons */}
        {(onEdit || onDelete) && (
          <div className="mt-3 flex gap-2">
            {onEdit && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onEdit(videoId);
                }}
                className="flex-1 flex items-center justify-center gap-1.5 bg-gray-700/40 hover:bg-red-600 text-gray-300 hover:text-white text-xs font-medium py-1.5 rounded-lg transition-all duration-200 border border-gray-700 hover:border-red-600"
              >
                <Pencil size={12} />
                Edit
              </button>
            )}
            {onDelete && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(videoId);
                }}
                className="flex-1 flex items-center justify-center gap-1.5 bg-red-600/10 hover:bg-red-600 text-red-500 hover:text-white text-xs font-medium py-1.5 rounded-lg transition-all duration-200 border border-red-600/20 hover:border-red-600"
              >
                <Trash2 size={12} />
                Delete
              </button>
            )}
          </div>
        )}

      </div>
    </div>
  );
}
