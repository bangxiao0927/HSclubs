package com.example.demo.club.model;

import java.time.Instant;

/**
 * Public projection of a {@link ClubPostComment} for the anonymous, unauthenticated comments
 * endpoint. Reuses the same no-PII rule as {@link PublicClubPost}: exposes only the comment's
 * own columns plus the author's display name and avatar, never {@code email},
 * {@code author_oauth_user_id}, {@code provider}, {@code provider_user_id}, {@code role},
 * {@code accepted_terms_at}, the user's {@code created_at}/{@code last_login_at}, or
 * {@code graduation_year}.
 *
 * <p>{@code createdAt} is an {@link Instant}, mirroring {@link PublicClubPost#getCreatedAt()}:
 * this is the other timestamp this API exposes to anonymous, possibly cross-timezone callers,
 * so it is serialized as an unambiguous, offset-bearing instant rather than the timezone-naive
 * {@link java.time.LocalDateTime} {@link ClubPostComment#getCreatedAt()} still uses internally.
 * See {@code EpochSecondsInstantTypeHandler} for how the mapper populates it correctly
 * regardless of the JDBC driver's own timezone conversion.
 */
public class PublicClubPostComment {

    private Long id;
    private Long postId;
    private String body;
    private Instant createdAt;
    private String authorDisplayName;
    private String authorAvatarUrl;

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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
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
