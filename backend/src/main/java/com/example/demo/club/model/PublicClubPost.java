package com.example.demo.club.model;

import java.time.LocalDateTime;

/**
 * Public projection of a {@link ClubPost} for the anonymous, unauthenticated club feed. Exposes
 * only the post's own columns plus the author's display name and avatar: never {@code email},
 * {@code uid}/{@code author_oauth_user_id}, {@code provider}, {@code provider_user_id},
 * {@code role}, {@code accepted_terms_at}, the user's {@code created_at}/{@code last_login_at},
 * or {@code graduation_year}. See ClubPostMapperTest for the regression test that a wildcard
 * select or a copied result map cannot silently reintroduce one of those columns here.
 */
public class PublicClubPost {

    private Long id;
    private Long clubId;
    private String title;
    private String imageUrl;
    private LocalDateTime pinnedAt;
    private LocalDateTime createdAt;
    private String authorDisplayName;
    private String authorAvatarUrl;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getAuthorDisplayName() {
        return authorDisplayName;
    }

    public void setAuthorDisplayName(String authorDisplayName) {
        this.authorDisplayName = authorDisplayName;
    }

    public String getAuthorAvatarUrl() {
        return authorAvatarUrl;
    }

    public void setAuthorAvatarUrl(String authorAvatarUrl) {
        this.authorAvatarUrl = authorAvatarUrl;
    }
}
