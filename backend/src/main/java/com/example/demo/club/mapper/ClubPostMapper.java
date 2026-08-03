package com.example.demo.club.mapper;

import com.example.demo.club.model.ClubPost;
import com.example.demo.club.model.ClubPostComment;
import com.example.demo.club.model.PublicClubPost;
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

    int insert(ClubPost post);

    int delete(@Param("id") Long id);

    int pin(@Param("id") Long id, @Param("pinnedByOauthUserId") Long pinnedByOauthUserId);

    int unpin(@Param("id") Long id);

    int countPinnedByClubId(@Param("clubId") Long clubId);

    List<ClubPostComment> findCommentsByPostId(@Param("postId") Long postId);

    int insertComment(ClubPostComment comment);

    int deleteComment(@Param("id") Long id);

    int countCommentsByPostId(@Param("postId") Long postId);

    List<String> findAllImageUrls();
}
