import { useState, useEffect, useRef } from "react";
import { toast } from "react-toastify";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { getAllCategories } from "@/service/CategoryService";
import { uploadVideo } from "@/service/VideoService";
import type { Category } from "@/models/Category";
import { CloudUpload, Film, Tag, FileText, X, Loader2, CheckCircle2, Clapperboard } from "lucide-react";


// Backend limit se MATCH hona zaroori hai.
// BACKEND/src/main/resources/application.yml → spring.servlet.multipart.max-file-size = 100MB
// Pehle yahan 200MB tha — user 200MB tak select kar sakta tha aur server pe
// "Upload Failed" fail ho jata tha (opaque error).
const MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024;
const MAX_FILE_SIZE_LABEL = "100MB";

const formatFileSize = (bytes: number) => {
  if (bytes >= 1024 * 1024 * 1024) return (bytes / (1024 * 1024 * 1024)).toFixed(1) + " GB";
  if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  return (bytes / 1024).toFixed(0) + " KB";
};

export default function VideoUpload() {
  // State for video metadata (Title, Description & Category)
  const [meta, setMeta] = useState({ title: "", description: "", category: "" });
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [videoPreview, setVideoPreview] = useState<string | null>(null);
  const [fileName, setFileName] = useState("No file selected");
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [showSuccess, setShowSuccess] = useState(false);
  const [dragging, setDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  // Categories load karo (dropdown ke liye)
  useEffect(() => {
    getAllCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  // Cleanup effect to revoke the object URL and avoid memory leaks
  useEffect(() => {
    return () => {
      if (videoPreview) URL.revokeObjectURL(videoPreview);
    };
  }, [videoPreview]);

  const pickFile = (file: File | undefined | null) => {
    if (!file) return;

    if (!file.type.startsWith("video/")) {
    toast.error("Only video files are allowed!");
    return;
  }
    // Size check — 200MB se badi file ko upload shuru karne se pehle hi reject karo
    if (file.size > MAX_FILE_SIZE_BYTES) {
      toast.error(`File too large! Max allowed size is ${MAX_FILE_SIZE_LABEL}.`);
      return;
    }

    // Revoke previous URL if user changes the file
    if (videoPreview) URL.revokeObjectURL(videoPreview);

    setSelectedFile(file);
    setFileName(file.name);
    setVideoPreview(URL.createObjectURL(file));
  };

  // Handle file selection
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    pickFile(e.target.files?.[0]);
    e.target.value = ""; // input reset — same file dobara select karne pe change event fire ho
  };

  // Drag & drop handlers
  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    pickFile(e.dataTransfer.files?.[0]);
  };

  // Main function to handle the upload process
  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!selectedFile) {
      toast.error("Please select a video file first!");
      return;
    }

    try {
      setUploading(true);
      setProgress(0);
      setShowSuccess(false);

      // Service layer se upload (Authorization interceptor handle karta hai)
      await uploadVideo(
        selectedFile,
        meta.title,
        meta.description,
        meta.category || undefined,
        (percent) => setProgress(percent)
      );

      setShowSuccess(true);
      toast.success("Video uploaded successfully!");
      navigate("/UserHome");

      // Optional: Reset form after success
      setMeta({ title: "", description: "", category: "" });
      setFileName("No file selected");
      setSelectedFile(null);

    } catch (error) {
      console.error("Upload Error:", error);
      const message =
        axios.isAxiosError(error) && error.response?.data?.message
          ? error.response.data.message
          : "Upload Failed";
      toast.error(message);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="relative overflow-hidden bg-[#181818] p-4 sm:p-6 rounded-2xl shadow-2xl border border-gray-800">
      {/* Decorative glow */}
      <div className="pointer-events-none absolute -top-20 -right-20 w-56 h-56 bg-red-600/10 rounded-full blur-3xl" />

      {/* Compact Header */}
      <div className="relative flex items-center gap-3 mb-5">
        <div className="w-10 h-10 rounded-xl bg-red-600/15 border border-red-600/30 flex items-center justify-center shrink-0">
          <Clapperboard className="text-red-600" size={22} />
        </div>
        <div>
          <h1 className="text-lg sm:text-2xl font-bold text-white uppercase tracking-tight leading-tight">
            Upload Your <span className="text-red-600">Video</span>
          </h1>
          <p className="text-gray-500 text-xs mt-0.5">Share your creativity with the world</p>
        </div>
      </div>

      <form className="relative space-y-5" onSubmit={handleUpload}>
        {/* Title + Category side by side (desktop) */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Video Title Input */}
          <div>
            <label className="flex items-center gap-1.5 text-xs font-semibold text-gray-400 mb-1.5">
              <Film size={13} className="text-red-600" />
              Title
            </label>
            <input
              type="text"
              required
              value={meta.title}
              onChange={(e) => setMeta({ ...meta, title: e.target.value })}
              className="w-full px-4 py-2.5 bg-gray-900/80 border border-gray-700 rounded-xl focus:ring-2 focus:ring-red-600 focus:border-red-600 outline-none text-white placeholder-gray-600 transition-all text-sm"
              placeholder="Enter a catchy title..."
            />
          </div>

          {/* Category Selection */}
          <div>
            <label className="flex items-center gap-1.5 text-xs font-semibold text-gray-400 mb-1.5">
              <Tag size={13} className="text-red-600" />
              Category
            </label>
            <select
              required
              value={meta.category}
              onChange={(e) => setMeta({ ...meta, category: e.target.value })}
              className="w-full px-4 py-2.5 bg-gray-900/80 border border-gray-700 rounded-xl focus:ring-2 focus:ring-red-600 focus:border-red-600 outline-none text-white cursor-pointer transition-all text-sm [&>option]:bg-gray-900"
            >
              <option value="" disabled>Select...</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.slug}>
                  {cat.name}
                </option>
              ))}
              {/* 'other' always available hai — agar admin ne koi category add nahi ki to bhi upload chale */}
              <option value="other">Other</option>
            </select>
          </div>
        </div>

        {/* File Selection Area — horizontal upload bar */}
        <div
          onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={handleDrop}
          onClick={() => !selectedFile && fileInputRef.current?.click()}
          className={`relative flex flex-col sm:flex-row items-center justify-between gap-4 sm:gap-6 px-5 sm:px-8 py-8 sm:py-10 border-2 border-dashed rounded-2xl transition-all duration-300 cursor-pointer overflow-hidden ${
            dragging
              ? "border-red-500 bg-red-600/10 scale-[1.01]"
              : selectedFile
                ? "border-green-500/50 bg-green-500/5 hover:border-green-500"
                : "border-gray-700 bg-gradient-to-br from-gray-900/60 to-gray-900/20 hover:border-red-600/60 hover:bg-gray-900/50"
          }`}
        >
          <input
            id="video-file-input"
            ref={fileInputRef}
            type="file"
            accept="video/*"
            onChange={handleFileChange}
            className="hidden"
          />

          {selectedFile ? (
            <>
              <div className="flex items-center gap-4 min-w-0 flex-1">
                <div className="w-12 h-12 rounded-xl bg-green-500/15 border border-green-500/30 flex items-center justify-center shrink-0">
                  <CheckCircle2 className="text-green-500" size={24} />
                </div>
                <div className="min-w-0 text-left">
                  <p className="text-white font-semibold text-sm truncate">{fileName}</p>
                  <p className="text-green-500/80 text-xs mt-1">
                    {formatFileSize(selectedFile.size)} — ready to upload
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setSelectedFile(null);
                  setFileName("No file selected");
                  if (videoPreview) URL.revokeObjectURL(videoPreview);
                  setVideoPreview(null);
                }}
                className="shrink-0 flex items-center gap-1.5 px-4 py-2 bg-gray-800 hover:bg-red-600 text-gray-300 hover:text-white rounded-lg text-sm font-medium transition-all cursor-pointer border border-gray-700 hover:border-red-600"
              >
                <X size={14} />
                Remove
              </button>
            </>
          ) : (
            <>
              <div className="flex items-center gap-4 min-w-0 text-left">
                <div
                  className={`w-14 h-14 rounded-2xl flex items-center justify-center shrink-0 transition-all duration-300 border ${
                    dragging
                      ? "bg-red-600 border-red-600 scale-110 shadow-lg shadow-red-600/40"
                      : "bg-red-600/10 border-red-600/30"
                  }`}
                >
                  <CloudUpload
                    className={dragging ? "text-white" : "text-red-600"}
                    size={28}
                  />
                </div>
                <div className="min-w-0">
                  <p className="text-white font-semibold text-base">
                    {dragging ? "Drop it here!" : "Drag & drop your video here"}
                  </p>
                  <p className="text-gray-500 text-xs mt-1">Max file size: 200MB</p>
                </div>
              </div>
              <label
                htmlFor="video-file-input"
                onClick={(e) => e.stopPropagation()}
                className="shrink-0 cursor-pointer inline-flex items-center gap-2 bg-red-600 hover:bg-red-700 px-6 py-3 rounded-xl font-bold text-sm transition-all shadow-lg shadow-red-600/25 hover:shadow-red-600/40 active:scale-95"
              >
                <CloudUpload size={18} />
                Choose File
              </label>
            </>
          )}
        </div>

        {/* Local Video Preview */}
        {videoPreview && (
          <div className="relative rounded-xl overflow-hidden border border-gray-700 bg-black shadow-inner">
            <video src={videoPreview} controls className="w-full max-h-72 object-contain" />
            <span className="absolute top-2 left-2 bg-black/70 text-gray-300 text-[10px] font-semibold px-2 py-0.5 rounded-full uppercase tracking-wide">
              Preview
            </span>
          </div>
        )}

        {/* Description Input */}
        <div>
          <label className="flex items-center gap-1.5 text-xs font-semibold text-gray-400 mb-1.5">
            <FileText size={13} className="text-red-600" />
            Description <span className="text-gray-600 font-normal">(Optional)</span>
          </label>
          <textarea
            rows={3}
            value={meta.description}
            onChange={(e) => setMeta({ ...meta, description: e.target.value })}
            className="w-full px-4 py-2.5 bg-gray-900/80 border border-gray-700 rounded-xl focus:ring-2 focus:ring-red-600 focus:border-red-600 outline-none text-white placeholder-gray-600 resize-none transition-all text-sm"
            placeholder="Tell your viewers about this video..."
          />
        </div>

        {/* Upload Progress Bar */}
        {uploading && (
          <div className="py-3 px-4 bg-gray-900/60 border border-gray-800 rounded-xl">
            <div className="flex justify-between items-center text-sm mb-2">
              <span className="text-gray-400 flex items-center gap-2">
                <Loader2 size={15} className="animate-spin text-red-600" />
                Uploading...
              </span>
              <span className="text-red-500 font-bold">{progress}%</span>
            </div>
            <div className="w-full h-2 bg-gray-800 rounded-full overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-red-700 to-red-500 rounded-full transition-all duration-300"
                style={{ width: `${progress}%` }}
              />
            </div>
            <p className="text-center text-xs text-gray-500 mt-2 animate-pulse">
              Please wait, video is being processed...
            </p>
          </div>
        )}

        {/* Success Feedback */}
        {showSuccess && (
          <div className="flex items-center gap-3 bg-green-500/10 border border-green-500/40 rounded-xl p-3">
            <CheckCircle2 className="text-green-500 shrink-0" size={20} />
            <p className="text-green-400 text-sm font-medium">
              <span className="font-bold">Success!</span> Video has been uploaded and is being processed.
            </p>
          </div>
        )}

        {/* Submit Button */}
        {!showSuccess && (
          <button
            type="submit"
            disabled={uploading}
            className={`w-full py-3 rounded-xl text-base font-bold uppercase tracking-widest transition-all flex items-center justify-center gap-2 ${
              uploading
                ? "bg-gray-700 cursor-not-allowed text-gray-400"
                : "bg-gradient-to-r from-red-700 to-red-600 hover:from-red-600 hover:to-red-500 active:scale-[0.98] shadow-lg shadow-red-600/25"
            }`}
          >
            {uploading ? (
              <>
                <Loader2 size={18} className="animate-spin" />
                Uploading {progress}%
              </>
            ) : (
              <>
                <CloudUpload size={18} />
                Publish Video
              </>
            )}
          </button>
        )}
      </form>
    </div>
  );
}
