package com.example.demo.club.mapper;

import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.model.ViewerMembershipStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ClubMapper {

    List<Club> findAll();

    Club findById(@Param("id") Long id);

    List<Club> findAllBySchoolId(@Param("schoolId") Long schoolId);

    List<Club> findAllBySchoolIdPaginated(@Param("schoolId") Long schoolId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    List<Club> searchBySchoolId(@Param("schoolId") Long schoolId,
                                @Param("name") String name,
                                @Param("category") String category,
                                @Param("alias") String alias,
                                @Param("advisor") String advisor,
                                @Param("query") String query,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    int countBySchoolId(@Param("schoolId") Long schoolId);

    Club findBySchoolIdAndSlug(@Param("schoolId") Long schoolId,
                               @Param("slug") String slug);

    Club findBySchoolIdAndId(@Param("schoolId") Long schoolId,
                             @Param("id") Long id);

    int insert(Club club);

    int update(Club club);

    int delete(@Param("id") Long id);

    ViewerMembershipStatus findMembershipStatus(@Param("clubId") Long clubId,
                                                @Param("email") String email);

    List<ClubMemberView> findMembersByClubId(@Param("clubId") Long clubId);

    int insertMember(@Param("clubId") Long clubId,
                     @Param("oauthUserId") Long oauthUserId,
                     @Param("roleName") String roleName);

    List<ClubMembershipRequest> findPendingRequestsByClubId(@Param("clubId") Long clubId);

    ClubMembershipRequest findPendingRequestByClubAndUser(@Param("clubId") Long clubId,
                                                          @Param("oauthUserId") Long oauthUserId);

    ClubMembershipRequest findMembershipRequestById(@Param("id") Long id);

    int insertMembershipRequest(@Param("clubId") Long clubId,
                                @Param("oauthUserId") Long oauthUserId);

    int deleteMembershipRequest(@Param("id") Long id);

    int updateMembershipRequestStatus(@Param("id") Long id,
                                       @Param("status") String status,
                                       @Param("reviewedByOauthUserId") Long reviewedByOauthUserId,
                                       @Param("note") String note);

    List<Club> findClubsByOauthUserId(@Param("oauthUserId") Long oauthUserId);

    List<ClubMembershipRequest> findPendingRequestsByOauthUserId(@Param("oauthUserId") Long oauthUserId);
}
