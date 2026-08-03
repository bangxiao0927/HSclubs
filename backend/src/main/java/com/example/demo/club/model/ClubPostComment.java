package com.example.demo.club.model;

import java.time.LocalDateTime;

/**
 * A flat (non-threaded) comment on a {@link ClubPost}.
 */
public class ClubPostComment {

    private Long id;
    private Long postId;
    private Long authorOauthUserId;
    private String body;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getAuthorOauthUserId() {
        return authorOauthUserId;
    }

    public void setAuthorOauthUserId(Long authorOauthUserId) {
        this.authorOauthUserId = authorOauthUserId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
