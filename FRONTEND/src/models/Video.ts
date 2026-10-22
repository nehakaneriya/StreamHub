export interface Video {
    videoId: string;
    title: string;
    description: string;
    contentType: string;
    category?: string;
    // NOTE: filePath backend se ab serialize nahi hota (@JsonIgnore) —
    // server ka internal path client ko expose nahi hona chahiye
    viewCount?: number;
    createdAt?: string;
    // Backend se flat fields aate hain
    userId?: string;
    userName?: string;
    // Sirf admin endpoints me aata hai — public feed/channel me nahi
    userEmail?: string;
    // Admin panel ke liye — video pe total comments
    commentCount?: number;
}

export interface VideoUploadRequest {
    title: string;
    description: string;
    category?: string;
    file: File;
}