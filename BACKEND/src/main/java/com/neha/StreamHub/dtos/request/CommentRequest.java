package com.neha.StreamHub.dtos.request;

// Naya comment post karte waqt frontend se aane wala body
// parentCommentId null = naya top-level comment
// parentCommentId set = ye kisi comment ka reply hai
public record CommentRequest(
        String text,
        String parentCommentId
) {
}
