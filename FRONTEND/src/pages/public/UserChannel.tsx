import { useEffect, useState } from "react";
import { useParams, Navigate, useNavigate } from "react-router-dom";
import { ArrowLeft, Video as VideoIcon } from "lucide-react";
import { getChannel } from "@/service/VideoService";
import VideoCard from "@/components/Video/VideoCard";
import { Spinner } from "@/components/ui/spinner";
import type { Video } from "@/models/Video";

interface ChannelData {
  userId: string;
  userName: string;
  videoCount: number;
  videos: Video[];
}

export default function UserChannel() {
  const { userId } = useParams();
  const navigate = useNavigate();
  const [channel, setChannel] = useState<ChannelData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!userId) return;
    getChannel(userId)
      .then(setChannel)
      .catch(() => setError("Channel not found."))
      .finally(() => setLoading(false));
  }, [userId]);

  if (!userId) return <Navigate to="/" replace />;

  const getInitials = (name?: string) => {
    if (!name) return "?";
    return name.split(" ").map((w) => w[0]).join("").toUpperCase().slice(0, 2);
  };


  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-screen bg-[#0f0f0f]">
        <Spinner className="w-12 h-12 text-red-600" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col justify-center items-center min-h-screen bg-[#0f0f0f] text-white gap-4">
        <VideoIcon size={48} className="text-red-600" />
        <p className="text-red-500 text-lg">{error}</p>
        <button
          onClick={() => navigate("/")}
          className="px-6 py-2 bg-red-600 rounded-full hover:bg-red-500 transition text-sm"
        >
          Go Home
        </button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#0f0f0f] text-white">
      <div className="max-w-7xl mx-auto px-4 pt-4">
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-gray-400 hover:text-white transition text-sm mb-6"
        >
          <ArrowLeft size={16} />
          Back
        </button>

        {/* Channel Header */}
        <div className="bg-[#181818] border border-gray-800 rounded-2xl p-6 mb-8">
          <div className="flex flex-col sm:flex-row items-center gap-5">
            <div className="w-20 h-20 rounded-full bg-red-600 flex items-center justify-center text-3xl font-bold text-white select-none shrink-0">
              {getInitials(channel?.userName)}
            </div>

            <div className="text-center sm:text-left min-w-0 flex-1">
              <h1 className="text-2xl font-bold truncate">{channel?.userName || "User"}</h1>
              <p className="text-gray-500 text-sm mt-1">Uploader</p>
            </div>

            <div className="flex gap-4 shrink-0">
              <div className="text-center">
                <p className="text-xl font-bold">{channel?.videoCount ?? 0}</p>
                <p className="text-gray-500 text-xs uppercase tracking-wide">
                  {channel?.videoCount === 1 ? "Video" : "Videos"}
                </p>
              </div>
             
            </div>
          </div>
        </div>

        {/* Videos */}
        {channel?.videos.length === 0 ? (
          <p className="text-center py-16 text-gray-500">No videos uploaded yet 🎬</p>
        ) : (
          <>
            <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-4">
              Uploads
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-8 pb-10">
              {channel?.videos.map((video) => (
                <VideoCard
                  key={video.videoId}
                  videoId={video.videoId}
                  title={video.title}
                  description={video.description}
                  userName={video.userName}
                  userId={video.userId}
                  contentType={video.contentType}
                  category={video.category}
                  createdAt={video.createdAt}
                  viewCount={video.viewCount}
                 
                />
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}