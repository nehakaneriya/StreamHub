import {useEffect,useState,useRef,useCallback} from "react";
import { useSearchParams } from "react-router-dom";
import VideoCard from "../../components/Video/VideoCard";
import type { Video } from "../../models/Video";
import type { Category } from "../../models/Category";
import { getAllVideos } from "../../service/VideoService";
import { getAllCategories } from "../../service/CategoryService";

export default function Home() {

  const [searchParams] = useSearchParams();
  const search = searchParams.get("search") || "";

  const [videos, setVideos] = useState<Video[]>([]);
  // Initially true — warna first load se pehle "No videos found." flash hota tha
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [scrollId, setScrollId] = useState<string | undefined>();
  const [hasNext, setHasNext] = useState(true);

  // Categories filter
  const [categories, setCategories] = useState<Category[]>([]);
  const [activeCategory, setActiveCategory] = useState("all");


  // Synchronous State Tracking Refs (Fixes Stale Closure Issues)
  // NOTE: Refs ko render phase me mutate nahi karte — React concurrent rendering
  // me render side-effects unsafe hote hain. Effect me sync karte hain.
  const loadingRef = useRef(loading);
  const hasNextRef = useRef(hasNext);
  const scrollIdRef = useRef(scrollId);
  const activeCategoryRef = useRef(activeCategory);
  const loadMoreRef = useRef<() => void>(() => {});

  useEffect(() => { loadingRef.current = loading; }, [loading]);
  useEffect(() => { hasNextRef.current = hasNext; }, [hasNext]);
  useEffect(() => { scrollIdRef.current = scrollId; }, [scrollId]);
  useEffect(() => { activeCategoryRef.current = activeCategory; }, [activeCategory]);

  const observer = useRef<IntersectionObserver | null>(null);
  const lastVideoElement = useRef<HTMLDivElement | null>(null);

  // Categories load karo (filter chips ke liye)
  useEffect(() => {
    getAllCategories()
      .then(setCategories)
      .catch(() => {
        // Error swallow karke chip list khaali kar dete the — user ko pata
        // nahi chalta ki categories load nahi hui
        console.error("Failed to load categories");
        setCategories([]);
      });
  }, []);

  // First Page
  const fetchFirstPage = useCallback(async () => {

    try {
      setLoading(true);
      const response = await getAllVideos(
        search,
        undefined,
        12,
        activeCategoryRef.current === "all" ? undefined : activeCategoryRef.current
      );

      // Safe Fallback: content defined nahi hone par empty array set karein
      const content = response?.content || [];
      setVideos(content);
      setScrollId(response?.scrollId ?? undefined);
      setHasNext(response?.hasNext ?? false);
      setError("");
    } catch (err) {

      console.error(err);
      setError("Unable to fetch videos.");
      setVideos([]); // Guard state against undefined
    } finally {
      setLoading(false);
    }
  }, [search]);

  // Pagination Loader
  const loadMore = useCallback(async () => {
    if (loadingRef.current || !hasNextRef.current) return;
    try {

      setLoading(true);
      const response = await getAllVideos(
        search,
        scrollIdRef.current,
        12,
        activeCategoryRef.current === "all" ? undefined : activeCategoryRef.current
      );

      const newContent = response?.content || [];
      setVideos(prev => [...prev, ...newContent]);
      setScrollId(response?.scrollId ?? undefined);
      setHasNext(response?.hasNext ?? false);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [search]);

  // loadMore ka latest reference rakhna — observer stale closure na pakde
  useEffect(() => { loadMoreRef.current = loadMore; }, [loadMore]);

  // Category click — reset aur first page fetch karo
  const handleCategoryClick = useCallback((slug: string) => {
    setActiveCategory(slug);
  }, []);

// Reset State on Search/Category Query Change
  useEffect(() => {
    setVideos([]);
    setScrollId(undefined);
    setHasNext(true);
    fetchFirstPage();
  }, [search, activeCategory, fetchFirstPage]);

  /**
   * IntersectionObserver setup.
   *
   * BUG FIX: Pehle observer `lastVideoRef` callback ref ke andar banaya tha,
   * jo sirf tab call hota tha jab last video element mount/unmount hota tha.
   * Category change par cleanup observer ko disconnect kar deta tha, lekin
   * observerdobara recreate nahi hota tha (kyunki last element identity badli
   * nahi) — infinite scroll permanently dead ho jati thi.
   *
   * Ab observer ek effect me banta hai jo videos.length par depend karta hai,
   * aur cleanup properly disconnect karta hai.
   */
  useEffect(() => {
    const node = lastVideoElement.current;
    if (!node) return;

    const obs = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasNextRef.current && !loadingRef.current) {
          loadMoreRef.current();
        }
      },
      { rootMargin: "200px" }
    );

    obs.observe(node);
    observer.current = obs;

    return () => {
      obs.disconnect();
      if (observer.current === obs) {
        observer.current = null;
      }
    };
  }, [videos.length, hasNext]);

  return (
    <div className="p-3 sm:p-6 bg-[#0f0f0f] min-h-screen text-white">
      <h2 className="text-xl sm:text-3xl font-bold mb-6 sm:mb-8 border-l-4 border-red-600 pl-3 sm:pl-4 uppercase animate-fade-in-up">
        {search ? `Search Results : "${search}"` : "Explore Videos"}
      </h2>

      {/* Category filter chips */}
      <div className="flex flex-wrap gap-2 mb-6 sm:mb-8">
        <button
          onClick={() => handleCategoryClick("all")}
          className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all cursor-pointer border ${
            activeCategory === "all"
              ? "bg-red-600 border-red-600 text-white shadow-lg shadow-red-600/30"
              : "bg-[#181818] border-gray-700 text-gray-300 hover:border-red-600/50 hover:text-white"
          }`}
        >
          All
        </button>
        {categories.map((cat) => (
          <button
            key={cat.id}
            onClick={() => handleCategoryClick(cat.slug)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all cursor-pointer border ${
              activeCategory === cat.slug
                ? "bg-red-600 border-red-600 text-white shadow-lg shadow-red-600/30"
                : "bg-[#181818] border-gray-700 text-gray-300 hover:border-red-600/50 hover:text-white"
            }`}
          >
            {cat.name}
          </button>
        ))}
        {/* 'other' special category hai — categories table me nahi hoti */}
        <button
          onClick={() => handleCategoryClick("other")}
          className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all cursor-pointer border ${
            activeCategory === "other"
              ? "bg-red-600 border-red-600 text-white shadow-lg shadow-red-600/30"
              : "bg-[#181818] border-gray-700 text-gray-300 hover:border-red-600/50 hover:text-white"
          }`}
        >
          Other
        </button>
      </div>

      {error && <p className="text-center text-red-500">{error}</p>}

      {videos.length === 0 && !loading && (
        <p className="text-center text-gray-400">No videos found.</p>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 sm:gap-8">
        {videos.map((video, index) => {
          const isLastElement = index === videos.length - 1;

          return (
            <div
              key={video.videoId}
              ref={isLastElement ? lastVideoElement : undefined}
            >
              <VideoCard
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
            </div>
          );
        })}
      </div>

      {loading && videos.length > 0 && (
        <p className="text-center mt-8 text-gray-400 animate-pulse">
          Loading more videos...
        </p>
      )}

      {!hasNext && videos.length > 0 && (
        <p className="text-center mt-8 text-gray-500">No more videos.</p>
      )}
    </div>
  );
}