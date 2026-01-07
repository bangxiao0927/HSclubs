package com.example.demo.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.demo.user.model.UserProfile;

@Mapper
public interface UserProfileMapper {

    UserProfile findByOauthUserId(@Param("oauthUserId") Long oauthUserId);

    void upsert(UserProfile profile);
}
