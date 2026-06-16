package com.example.demo.school.mapper;

import com.example.demo.school.model.SchoolUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SchoolUserMapper {

    List<SchoolUser> findByOauthUserId(@Param("oauthUserId") Long oauthUserId);

    List<SchoolUser> findBySchoolId(@Param("schoolId") Long schoolId);

    SchoolUser findBySchoolAndUser(@Param("schoolId") Long schoolId,
                                   @Param("oauthUserId") Long oauthUserId);

    int insert(SchoolUser schoolUser);

    int updateRole(@Param("schoolId") Long schoolId,
                   @Param("oauthUserId") Long oauthUserId,
                   @Param("role") String role);

    int deleteBySchoolAndUser(@Param("schoolId") Long schoolId,
                              @Param("oauthUserId") Long oauthUserId);
}
