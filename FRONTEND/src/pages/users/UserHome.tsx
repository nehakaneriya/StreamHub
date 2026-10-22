import { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import EditVideoModal from "../../components/Video/EditVideoModal";
import type { Video } from "../../models/Video";
import { deleteVideo, getMyVideos, getThumbnailUrl } from "../../service/VideoService";
import { getCurrentUser } from "@/service/Authservice";
import useAuthStore from "@/auth/store";
import type UserT from "@/models/User";
import { toast } from "react-toastify";
import { Trash2, Pencil, Tag, MessageCircle, Eye } from "lucide-react";

export default function UserHome() {

  const [videos, setVideos] = useState<Video[]>([]);
  const [loading, setLoading] = useState(true);
  const [user1, setUser1] = useState<UserT | null>(null);
  const [editVideo, setEditVideo] = useState<Video | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);

  const fetchVideos = useCallback(async () => {
    try {
      const data = await getMyVideos();
      setVideos(data);
    } catch {
      console.error("Failed to fetch videos");
      toast.error("Failed to load videos");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const getUserData = async () => {
      try {
        if (!user?.email) return;
        const data = await getCurrentUser(user.email);
        setUser1(data);
      } catch (error) {
        console.error("Failed to fetch user data:", error);
        toast.error("Failed to load user data");
      }
    };

    getUserData();
    fetchVideos();
  }, [user?.email, fetchVideos]);

  // 🔹 Delete Video
  const handleDelete = async (id: string) => {
    try {
      setDeletingId(id);
      await deleteVideo(id);

      setVideos((prev) =>
        prev.filter((v) => v.videoId !== id)
      );

      toast.success("Video deleted successfully");
    } catch {
      console.error("Delete failed");
      toast.error("Failed to delete video");
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="p-6 bg-[#0f0f0f] min-h-screen text-white">

      {/* Header — profile style: name + email */}
      <div className="flex flex-col sm:flex-row flex-wrap items-center justify-between gap-4 mb-8">
        <div className="flex items-center gap-4">
          {/* Avatar */}
          <div className="w-14 h-14 rounded-full bg-red-600 flex items-center justify-center text-2xl font-bold text-white select-none shrink-0">
            {(user1?.name || user?.name || "U").charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0">
            <h2 className="text-xl sm:text-2xl font-bold truncate">
              {user1?.name || user?.name || "User"}
            </h2>
            <p className="text-gray-400 text-sm truncate">
              {user?.email || user1?.email}
            </p>
          </div>
        </div>

        <button
          onClick={() => navigate("/UserHome/upload")}
          className="bg-red-600 px-6 py-2 rounded-lg hover:bg-red-700 transition cursor-pointer text-sm sm:text-base"
        >
          + Upload Video
        </button>

      </div>

      {/* Loading */}
      {loading && (
        <p className="text-center text-gray-400">
          Loading videos...
        </p>
      )}

      {/* No Videos */}
      {!loading && videos.length === 0 && (
        <p className="text-center text-gray-400">
          No videos uploaded yet 🎬
        </p>
      )}

      {/* Videos Table */}
      {!loading && videos.length > 0 && (
        <div className="bg-[#181818] border border-gray-800 rounded-xl overflow-x-auto">
          <table className="w-full text-left min-w-[800px]">
            <thead className="bg-[#111] border-b border-gray-800">
              <tr>
                <th className="p-4 text-gray-400 text-sm">Title</th>
                <th className="p-4 text-gray-400 text-sm">Category</th>
                <th className="p-4 text-gray-400 text-sm">Views</th>
                <th className="p-4 text-gray-400 text-sm">Comments</th>
                <th className="p-4 text-gray-400 text-sm">Uploaded</th>
                <th className="p-4 text-gray-400 text-sm">Actions</th>
              </tr>
            </thead>

            <tbody>
              {videos.map((video) => (
                <tr
                  key={video.videoId}
                  className="border-b border-gray-800 hover:bg-[#1f1f1f] transition"
                >
                  {/* Title */}
                  <td className="p-4">
                    <div className="flex items-center gap-3">
                      {/* Video thumbnail — server-generated first frame */}
                      <div className="relative w-32 aspect-video bg-black rounded-lg overflow-hidden shrink-0">
                        <img
                          src={getThumbnailUrl(video.videoId)}
                          alt={video.title}
                          className="w-full h-full object-cover"
                          loading="lazy"
                          onError={(e) => {
                            e.currentTarget.style.visibility = "hidden";
                          }}
                        />
                      </div>
                      <div className="min-w-0">
                        <button
                          onClick={() => navigate(`/watch/${video.videoId}`)}
                          className="font-medium hover:text-red-400 transition text-left cursor-pointer line-clamp-1"
                          title={video.title}
                        >
                          {video.title}
                        </button>
                        {video.description && (
                          <p className="text-gray-500 text-xs truncate max-w-[280px]">
                            {video.description}
                          </p>
                        )}
                      </div>
                    </div>
                  </td>

                  {/* Category */}
                  <td className="p-4">
                    <span className="inline-flex items-center gap-1.5 bg-red-600/15 text-red-400 px-2.5 py-1 rounded-full text-xs font-medium capitalize">
                      <Tag size={11} />
                      {video.category || "other"}
                    </span>
                  </td>

                  {/* Views */}
                  <td className="p-4">
                    <span className="inline-flex items-center gap-1.5 text-gray-300 text-sm">
                      <Eye size={14} className="text-gray-500" />
                      {(video.viewCount ?? 0).toLocaleString()}
                    </span>
                  </td>

                  {/* Comments */}
                  <td className="p-4">
                    <span className="inline-flex items-center gap-1.5 text-gray-300 text-sm">
                      <MessageCircle size={14} className="text-gray-500" />
                      {video.commentCount ?? 0}
                    </span>
                  </td>

                  {/* Uploaded At */}
                  <td className="p-4 text-gray-400 text-sm">
                    {video.createdAt
                      ? new Date(video.createdAt).toLocaleDateString()
                      : "—"}
                  </td>

                  {/* Actions */}
                  <td className="p-4">
                    <div className="flex gap-2">
                      <button
                        onClick={() => setEditVideo(video)}
                        className="inline-flex items-center justify-center gap-1 min-w-[85px] px-3 py-2 bg-[#222] hover:bg-red-600 text-gray-300 hover:text-white text-sm font-medium rounded-lg border border-gray-600 hover:border-red-600 transition cursor-pointer"
                      >
                        <Pencil size={12} />
                        Edit
                      </button>
                      <button
                        onClick={() => handleDelete(video.videoId)}
                        disabled={deletingId === video.videoId}
                        className="flex items-center gap-1 px-3 py-1 bg-red-600/10 hover:bg-red-600 text-red-500 hover:text-white text-xs font-medium rounded-lg border border-red-600/20 hover:border-red-600 transition disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                      >
                        <Trash2 size={12} />
                        {deletingId === video.videoId ? "..." : "Delete"}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Edit Video Modal */}
      {editVideo && (
        <EditVideoModal
          videoId={editVideo.videoId}
          currentTitle={editVideo.title}
          currentDescription={editVideo.description}
          currentCategory={editVideo.category}
          onClose={() => setEditVideo(null)}
          onSaved={fetchVideos}
        />
      )}

    </div>
  );
}