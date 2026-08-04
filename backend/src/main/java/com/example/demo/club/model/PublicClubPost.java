package com.example.demo.club.model;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Public projection of a {@link ClubPost} for the anonymous, unauthenticated club feed. Exposes
 * only the post's own columns plus the author's display name and avatar: never {@code email},
 * {@code uid}/{@code author_oauth_user_id}, {@code provider}, {@code provider_user_id},
 * {@code role}, {@code accepted_terms_at}, the user's {@code created_at}/{@code last_login_at},
 * or {@code graduation_year}. See ClubPostMapperTest for the regression test that a wildcard
 * select or a copied result map cannot silently reintroduce one of those columns here.
 *
 * <p>{@code createdAt} is an {@link Instant}, not a {@link LocalDateTime} like every other
 * timestamp in this codebase: it is the one timestamp this API exposes to anonymous, possibly
 * cross-timezone callers, so it is serialized as an unambiguous, offset-bearing instant (Jackson
 * writes {@code Instant} as ISO-8601 with a trailing {@code Z}) rather than a timezone-naive
 * wall-clock string a browser could misinterpret as its own local time. See
 * {@code EpochSecondsInstantTypeHandler} for how the mapper populates it correctly regardless of
 * the JDBC driver's own timezone conversion.
 */
public class PublicClubPost {

    private Long id;
    private Long clubId;
    private String title;
    private String imageUrl;
    private LocalDateTime pinnedAt;
    private Instant createdAt;
    private String authorDisplayName;
    private String authorAvatarUrl;

    // A correlated COUNT(*) against club_post_comment (see ClubPostMapper.xml's
    // PublicPostColumnList), never a join: a join against club_post_comment would multiply
    // this row once per comment instead of contributing a single count.
    private int commentCount;

    // Computed server-side per request from the viewer's own oauth_user_id and the club's
    // canManage/platform-owner status (see ClubPostMapper.xml's PublicPostColumnList) -- never
    // the viewer's or the author's oauth_user_id itself. This is what lets the frontend show a
    // delete control to the post's own author without either leaking author_oauth_user_id to
    // every caller or having the frontend infer authorship by comparing display names.
    private boolean viewerCanDelete;

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

    public int getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }

    public boolean isViewerCanDelete() {
        return viewerCanDelete;
    }

    public void setViewerCanDelete(boolean viewerCanDelete) {
        this.viewerCanDelete = viewerCanDelete;
    }
}
