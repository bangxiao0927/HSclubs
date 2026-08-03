package com.example.demo.club.mapper;

import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.ClubPostComment;
import com.example.demo.club.model.PublicClubPost;
import com.example.demo.club.model.PublicClubPostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ClubPostMapper {

    List<ClubPost> findFeedByClubId(@Param("clubId") Long clubId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countFeedByClubId(@Param("clubId") Long clubId);

    // Public projection for the anonymous club feed: post columns plus the author's display
    // name/avatar only, never any oauth_users column that could identify or contact them (see
    // PublicClubPost's Javadoc for the exact forbidden list).
    List<PublicClubPost> findPublicFeedByClubId(@Param("clubId") Long clubId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    // Read-back for ClubPostService#publish, so a just-published post can be returned with the
    // exact same safe shape a feed item has (author display name/avatar, never the internal
    // author_oauth_user_id). Scoped to clubId as a defense-in-depth guard, matching every other
    // by-ID lookup in this codebase.
    PublicClubPost findPublicPostByIdAndClubId(@Param("id") Long id, @Param("clubId") Long clubId);

    // Scoped lookup shared by the pin/unpin path (ClubPostService#pin/#unpin) and the delete
    // endpoint's own author/president/platform-owner authorization: a post ID that exists but
    // under a different club must read back null, exactly like every other by-ID lookup in this
    // mapper, so cross-club pinning/deletion 404s instead of silently touching someone else's
    // post. Exposes author_oauth_user_id and image_url, so it must never be serialized directly
    // as a controller response body.
    ClubPost findByIdAndClubId(@Param("id") Long id, @Param("clubId") Long clubId);

    int insert(ClubPost post);

    int delete(@Param("id") Long id);

    int pin(@Param("id") Long id, @Param("pinnedByOauthUserId") Long pinnedByOauthUserId);

    int unpin(@Param("id") Long id);

    int countPinnedByClubId(@Param("clubId") Long clubId);

    // The pessimistic lock the comment cap needs: SELECT ... FOR UPDATE, scoped to clubId as the
    // same defense-in-depth guard every other by-ID lookup in this mapper applies. Returns the
    // locked row's id, or null if no row matched -- a FOR UPDATE matching zero rows takes no
    // lock, so a null here means "missing post", not "lock acquired on nothing".
    Long lockPostIdForUpdate(@Param("id") Long id, @Param("clubId") Long clubId);

    List<ClubPostComment> findCommentsByPostId(@Param("postId") Long postId);

    // Raw (non-public) read-back scoped to its post, for the delete endpoint's own author/
    // president/platform-owner authorization: exposes author_oauth_user_id, so it must never be
    // serialized directly as a controller response body.
    ClubPostComment findCommentByIdAndPostId(@Param("id") Long id, @Param("postId") Long postId);

    // Public projection for the anonymous comments endpoint: comment columns plus the author's
    // display name/avatar only, reusing the same no-PII rule as findPublicFeedByClubId.
    List<PublicClubPostComment> findPublicCommentsByPostId(@Param("postId") Long postId);

    // Read-back for ClubPostCommentWriter#lockCountAndInsert, so a just-posted comment can be
    // returned in the exact same safe shape the public list has.
    PublicClubPostComment findPublicCommentByIdAndPostId(@Param("id") Long id, @Param("postId") Long postId);

    int insertComment(ClubPostComment comment);

    int deleteComment(@Param("id") Long id);

    int countCommentsByPostId(@Param("postId") Long postId);

    List<String> findAllImageUrls();
}
