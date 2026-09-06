package com.example.demo.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper {

    void clearClubApprovalReferences(@Param("oauthUserId") Long oauthUserId);

    void clearMembershipReviewReferences(@Param("oauthUserId") Long oauthUserId);

    void clearPostPinReferences(@Param("oauthUserId") Long oauthUserId);

    void deleteAuthoredComments(@Param("oauthUserId") Long oauthUserId);

    void deleteAuthoredPosts(@Param("oauthUserId") Long oauthUserId);

    void deleteMembershipRequests(@Param("oauthUserId") Long oauthUserId);

    void deleteMemberships(@Param("oauthUserId") Long oauthUserId);

    void deleteProfile(@Param("oauthUserId") Long oauthUserId);
}
