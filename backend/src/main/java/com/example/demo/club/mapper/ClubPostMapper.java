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
