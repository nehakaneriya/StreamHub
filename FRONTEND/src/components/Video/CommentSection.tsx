import { useEffect, useState, useCallback } from "react";
import { toast } from "react-toastify";
import axios from "axios";
import { MessageCircle, Send, Trash2, Reply as ReplyIcon, EyeOff, Eye, Loader2 } from "lucide-react";
import useAuthStore from "@/auth/store";
import { getComments, postComment, deleteComment, hideCommentAdmin, unhideCommentAdmin } from "@/service/CommentService";
import type { Comment } from "@/models/Comment";

interface CommentSectionProps {
  videoId: string;
}

export default function CommentSection({ videoId }: CommentSectionProps) {
  const authStatus = useAuthStore((state) => state.authStatus);
  const currentUser = useAuthStore((state) => state.user);

  const isAdmin = currentUser?.roles?.some(
    (r) => r.name === "ROLE_ADMIN" || r.name === "ADMIN"
  );

  const [comments, setComments] = useState<Comment[]>([]);
  const [text, setText] = useState("");
  const [loading, setLoading] = useState(true);
  const [posting, setPosting] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  // Reply-specific state — kaunse comment ka reply-box khula hai, aur uska text
  const [replyingTo, setReplyingTo] = useState<string | null>(null);
  const [replyText, setReplyText] = useState("");
  const [postingReply, setPostingReply] = useState(false);

  const loadComments = useCallback(async () => {
    try {
      const data = await getComments(videoId);
      setComments(data);
    } catch {
      toast.error("Failed to load comments.");
    } finally {
      setLoading(false);
    }
  }, [videoId]);

  useEffect(() => {
    loadComments();
  }, [loadComments]);

  // Total count — top-level comments + unke replies bhi ginenge
  const totalCount = comments.reduce((sum, c) => sum + 1 + (c.replies?.length || 0), 0);

  const handlePost = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!text.trim()) {
      toast.error("Comment cannot be empty!");
      return;
    }

    try {
      setPosting(true);
      const newComment = await postComment(videoId, text.trim());
      // Naya comment turant top pe dikhao (dobara poori list fetch karne ki zaroorat nahi)
      setComments((prev) => [{ ...newComment, replies: [] }, ...prev]);
      setText("");
    } catch (error) {
      const message =
        axios.isAxiosError(error) && error.response?.data?.message
          ? error.response.data.message
          : "Failed to post comment.";
      toast.error(message);
    } finally {
      setPosting(false);
    }
  };

  const handlePostReply = async (parentCommentId: string) => {
    if (!replyText.trim()) {
      toast.error("Reply cannot be empty!");
      return;
    }

    try {
      setPostingReply(true);
      const newReply = await postComment(videoId, replyText.trim(), parentCommentId);
      // Sirf usi parent comment ke replies array mein naya reply add karo
      setComments((prev) =>
        prev.map((c) =>
          c.id === parentCommentId
            ? { ...c, replies: [...(c.replies || []), newReply] }
            : c
        )
      );
      setReplyText("");
      setReplyingTo(null);
    } catch (error) {
      const message =
        axios.isAxiosError(error) && error.response?.data?.message
          ? error.response.data.message
          : "Failed to post reply.";
      toast.error(message);
    } finally {
      setPostingReply(false);
    }
  };

  const handleDelete = async (commentId: string, parentCommentId?: string | null) => {
    try {
      await deleteComment(commentId);
      if (parentCommentId) {
        // Ye ek reply thi — usi parent ke replies array se hatao
        setComments((prev) =>
          prev.map((c) =>
            c.id === parentCommentId
              ? { ...c, replies: (c.replies || []).filter((r) => r.id !== commentId) }
              : c
          )
        );
      } else {
        // Top-level comment thi (uske saath uske replies bhi backend se delete ho gaye)
        setComments((prev) => prev.filter((c) => c.id !== commentId));
      }
      toast.success("Comment deleted.");
    } catch {
      toast.error("Failed to delete comment.");
    }
  };

  // Admin toggle hide / unhide directly from video comment section
  const handleToggleHide = async (commentId: string, isCurrentlyHidden?: boolean) => {
    try {
      setTogglingId(commentId);
      if (isCurrentlyHidden) {
        await unhideCommentAdmin(commentId);
        toast.success("Comment is now visible to public");
      } else {
        await hideCommentAdmin(commentId);
        toast.success("Comment hidden from public view");
      }

      setComments((prev) =>
        prev.map((c) => {
          if (c.id === commentId) {
            return { ...c, hidden: !isCurrentlyHidden };
          }
          if (c.replies) {
            return {
              ...c,
              replies: c.replies.map((r) =>
                r.id === commentId ? { ...r, hidden: !isCurrentlyHidden } : r
              ),
            };
          }
          return c;
        })
      );
    } catch {
      toast.error("Failed to update comment status");
    } finally {
      setTogglingId(null);
    }
  };

  const getInitials = (name?: string) => {
    if (!name) return "?";
    return name.split(" ").map((w) => w[0]).join("").toUpperCase().slice(0, 2);
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString("en-IN", {
      day: "numeric",
      month: "short",
      year: "numeric",
    });
  };

  // Ek comment row render karta hai — top-level aur reply dono ke liye reuse hota hai
  const renderComment = (comment: Comment, isReply: boolean) => {
    // Ownership check userId se — public comment response me email expose nahi hota
    const isOwner = !!currentUser?.id && currentUser.id === comment.userId;
    const isHidden = !!comment.hidden;

    return (
      <div key={comment.id} className="flex items-start gap-3">
        <div
          className={`rounded-full bg-gray-700 flex items-center justify-center text-white font-bold shrink-0 ${
            isReply ? "w-7 h-7 text-[10px]" : "w-9 h-9 text-xs"
          } ${isHidden ? "opacity-50" : ""}`}
        >
          {getInitials(comment.userName)}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className={`text-sm font-semibold ${isHidden ? "text-gray-400" : "text-white"}`}>
              {comment.userName || "Unknown"}
            </span>
            <span className="text-gray-500 text-xs">
              {formatDate(comment.createdAt)}
            </span>
            {isHidden && (
              <span className="text-[11px] font-medium text-yellow-500 bg-yellow-500/10 border border-yellow-600/30 px-2 py-0.5 rounded-full flex items-center gap-1">
                <EyeOff size={10} />
                Hidden
              </span>
            )}
          </div>

          {/* Comment Body */}
          {isHidden ? (
            isAdmin ? (
              // Admin can see original text with warning notice
              <div className="mt-1.5 p-2.5 rounded-lg bg-yellow-950/20 border border-yellow-800/40 text-xs text-yellow-500/90">
                <p className="font-medium flex items-center gap-1 mb-1">
                  <EyeOff size={12} />
                  This {isReply ? "reply" : "comment"} is hidden from public view
                </p>
                <p className="text-gray-300 italic whitespace-pre-line break-words text-sm">
                  {comment.text}
                </p>
              </div>
            ) : (
              // Regular user sees clear hidden notice
              <div className="mt-1.5 p-2.5 rounded-lg bg-yellow-950/15 border border-yellow-800/30 text-xs text-yellow-500/90 flex items-center gap-2 italic">
                <EyeOff size={13} className="shrink-0 text-yellow-500" />
                <span>This {isReply ? "reply" : "comment"} has been hidden by admin.</span>
              </div>
            )
          ) : (
            <p className="text-gray-300 text-sm mt-1 whitespace-pre-line break-words">
              {comment.text}
            </p>
          )}

          {/* Reply button — sirf unhidden top-level comments pe (1-level-deep restriction) */}
          {!isReply && !isHidden && authStatus && (
            <button
              onClick={() =>
                setReplyingTo(replyingTo === comment.id ? null : comment.id)
              }
              className="flex items-center gap-1 text-gray-500 hover:text-red-500 text-xs font-medium mt-2 transition cursor-pointer"
            >
              <ReplyIcon size={12} />
              Reply
            </button>
          )}

          {/* Inline reply input box */}
          {!isReply && !isHidden && replyingTo === comment.id && (
            <div className="flex items-start gap-2 mt-3">
              <div className="w-7 h-7 rounded-full bg-red-600 flex items-center justify-center text-white font-bold text-[10px] shrink-0">
                {getInitials(currentUser?.name)}
              </div>
              <div className="flex-1">
                <textarea
                  value={replyText}
                  onChange={(e) => setReplyText(e.target.value)}
                  placeholder={`Reply to ${comment.userName || "this comment"}...`}
                  rows={2}
                  maxLength={1000}
                  autoFocus
                  className="w-full p-2.5 bg-gray-900 border border-gray-700 rounded-lg focus:ring-2 focus:ring-red-600 outline-none text-white text-sm resize-none"
                />
                <div className="flex justify-end gap-2 mt-2">
                  <button
                    onClick={() => {
                      setReplyingTo(null);
                      setReplyText("");
                    }}
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-400 hover:text-white transition cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={() => handlePostReply(comment.id)}
                    disabled={postingReply || !replyText.trim()}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition cursor-pointer ${
                      postingReply || !replyText.trim()
                        ? "bg-gray-700 text-gray-400 cursor-not-allowed"
                        : "bg-red-600 hover:bg-red-700 text-white"
                    }`}
                  >
                    <Send size={12} />
                    {postingReply ? "Posting..." : "Reply"}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Nested replies */}
          {!isReply && comment.replies && comment.replies.length > 0 && (
            <div className="flex flex-col gap-3 mt-3 pl-4 border-l border-gray-800">
              {comment.replies.map((reply) => renderComment(reply, true))}
            </div>
          )}
        </div>

        {/* Action buttons: Admin Hide/Unhide toggle + Owner/Admin Delete */}
        <div className="flex items-center gap-1 shrink-0 pt-0.5">
          {isAdmin && (
            <button
              onClick={() => handleToggleHide(comment.id, isHidden)}
              disabled={togglingId === comment.id}
              className={`p-1.5 rounded transition cursor-pointer disabled:opacity-40 ${
                isHidden
                  ? "text-yellow-500 hover:text-yellow-300 hover:bg-yellow-950/30"
                  : "text-gray-500 hover:text-yellow-500 hover:bg-gray-800"
              }`}
              title={isHidden ? "Unhide comment" : "Hide comment from public"}
            >
              {togglingId === comment.id ? (
                <Loader2 size={14} className="animate-spin" />
              ) : isHidden ? (
                <Eye size={14} />
              ) : (
                <EyeOff size={14} />
              )}
            </button>
          )}

          {(isOwner || isAdmin) && (
            <button
              onClick={() => handleDelete(comment.id, comment.parentCommentId)}
              className="text-gray-500 hover:text-red-600 transition p-1.5 rounded hover:bg-gray-800 cursor-pointer"
              title="Delete comment"
            >
              <Trash2 size={14} />
            </button>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="mt-6 bg-[#1a1a1a] rounded-xl p-4 border border-gray-800">
      {/* Header */}
      <div className="flex items-center gap-2 mb-4">
        <MessageCircle size={18} className="text-red-600" />
        <h2 className="text-white font-semibold text-sm">
          {totalCount} {totalCount === 1 ? "Comment" : "Comments"}
        </h2>
      </div>

      {/* Comment Input — sirf logged-in users ke liye */}
      {authStatus ? (
        <form onSubmit={handlePost} className="flex items-start gap-3 mb-6">
          <div className="w-9 h-9 rounded-full bg-red-600 flex items-center justify-center text-white font-bold text-xs shrink-0">
            {getInitials(currentUser?.name)}
          </div>
          <div className="flex-1">
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Add a comment..."
              rows={2}
              maxLength={1000}
              className="w-full p-3 bg-gray-900 border border-gray-700 rounded-xl focus:ring-2 focus:ring-red-600 outline-none text-white text-sm resize-none"
            />
            <div className="flex justify-end mt-2">
              <button
                type="submit"
                disabled={posting || !text.trim()}
                className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold transition cursor-pointer ${
                  posting || !text.trim()
                    ? "bg-gray-700 text-gray-400 cursor-not-allowed"
                    : "bg-red-600 hover:bg-red-700 text-white"
                }`}
              >
                <Send size={14} />
                {posting ? "Posting..." : "Comment"}
              </button>
            </div>
          </div>
        </form>
      ) : (
        <p className="text-gray-500 text-sm mb-6">Login to add a comment.</p>
      )}

      {/* Comments List */}
      {loading ? (
        <p className="text-gray-500 text-sm">Loading comments...</p>
      ) : comments.length === 0 ? (
        <p className="text-gray-500 text-sm">No comments yet. Be the first to comment!</p>
      ) : (
        <div className="flex flex-col gap-4">
          {comments.map((comment) => renderComment(comment, false))}
        </div>
      )}
    </div>
  );
}
