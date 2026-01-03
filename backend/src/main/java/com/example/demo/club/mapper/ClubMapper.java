package com.example.demo.club.mapper;

import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ViewerMembershipStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ClubMapper {

    List<Club> findAll();

    Club findById(@Param("id") Long id);

    int insert(Club club);

    int update(Club club);

    int delete(@Param("id") Long id);

    ViewerMembershipStatus findMembershipStatus(@Param("clubId") Long clubId, @Param("email") String email);

    List<ClubMemberView> findMembersByClubId(@Param("clubId") Long clubId);

    int insertMember(@Param("clubId") Long clubId, @Param("oauthUserId") Long oauthUserId);
}
