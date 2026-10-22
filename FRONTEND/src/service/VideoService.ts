import apiClient from "../config/ApiClient";
import { hlsMasterUrl, thumbnailUrl } from "../config/env";
import type { Video } from "../models/Video";
import type { ScrollResponse } from "../models/ScrollResponse";

export const uploadVideo = async (
    file: File,
    title: string,
    description: string,
    category?: string,
    onProgress?: (percent: number) => void
) => {
    // 1. File upload ke liye FormData zaroori hai
    const formData = new FormData();
    formData.append("file", file);
    formData.append("title", title);
    formData.append("description", description);
    if (category) {
        formData.append("category", category);
    }

    // 2. POST request with multipart header
    const response = await apiClient.post("/videos", formData, {
        headers: {
            "Content-Type": "multipart/form-data"
        },
        // Upload + FFmpeg processing lamba le sakta hai (10 min cap)
        timeout: 10 * 60 * 1000,
        onUploadProgress: (progressEvent) => {
            if (onProgress) {
                onProgress(Math.round((progressEvent.loaded * 100) / (progressEvent.total || 1)));
            }
        },
    });
    return response.data;
};



export const getMyVideos = async (): Promise<Video[]> => {
    // User ke apne videos fetch karne ke liye
    const response = await apiClient.get("/videos/my-videos");
    return response.data;
};

export const getAllVideos = async (
    search: string = "",
    scrollId?: string,
    pageSize: number = 12,
    category?: string
): Promise<ScrollResponse<Video>> => {
    // Public feed ke liye
    const response = await apiClient.get("/videos", {
        params: {
            search,
            scrollId,
            pageSize,
            category
        }
    });
    return response.data;
};

export const deleteVideo = async (videoId: string) => {
    // Video delete karne ke liye
    const response = await apiClient.delete(`/videos/${videoId}`);
    return response.data;
};

export const updateVideo = async (
    videoId: string,
    data: { title: string; description?: string; category?: string }
): Promise<Video> => {
    // Video title/description update karne ke liye
    const response = await apiClient.put(`/videos/${videoId}`, data);
    return response.data;
};

// HLS Master URL generate karne ka helper
export const getHlsMasterUrl = (videoId: string) => hlsMasterUrl(videoId);

// Thumbnail URL — feed ke cards isse use karte hain.
// Pehle har card apna <video> element se HLS master playlist load karta tha
// (ek page par 12+ playlist + segment requests).
export const getThumbnailUrl = (videoId: string) => thumbnailUrl(videoId);

// Unique view record karta hai — same user + same video lifetime ek baar
export const incrementView = async (videoId: string): Promise<number> => {
    const response = await apiClient.post(`/videos/${videoId}/view`);
    return response.data?.viewCount ?? 0;
};

// Public channel page — kisi bhi user ke videos (bina login)
export const getChannel = async (userId: string) => {
    const response = await apiClient.get(`/videos/user/${userId}`);
    return response.data;
};