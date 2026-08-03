package com.example.demo.club.model;

import java.time.LocalDateTime;

/**
 * A single photo post on a club's media feed. A post always has exactly one photo; pinning
 * (setting pinnedAt/pinnedByOauthUserId) moves it to the front of the same paginated feed
 * rather than into a separate list.
 */
public class ClubPost {

    private Long id;
    private Long clubId;
    private Long authorOauthUserId;
    private String title;
    private String imageUrl;
    private LocalDateTime pinnedAt;
    private Long pinnedByOauthUserId;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClubId() {
        return clubId;
    }

    public void setClubId(Long clubId) {
        this.clubId = clubId;
    }

    public Long getAuthorOauthUserId() {
        return authorOauthUserId;
    }

    public void setAuthorOauthUserId(Long authorOauthUserId) {
        this.authorOauthUserId = authorOauthUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDateTime getPinnedAt() {
        return pinnedAt;
    }

    public void setPinnedAt(LocalDateTime pinnedAt) {
        this.pinnedAt = pinnedAt;
    }

    public Long getPinnedByOauthUserId() {
        return pinnedByOauthUserId;
    }

    public void setPinnedByOauthUserId(Long pinnedByOauthUserId) {
        this.pinnedByOauthUserId = pinnedByOauthUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
