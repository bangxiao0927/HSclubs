package com.example.demo.auth.mapper;

import com.example.demo.auth.model.OAuthUserRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OAuthUserMapper {

    void upsert(OAuthUserRecord record);

    Long findIdByEmail(@Param("email") String email);

    LocalDateTime findCreatedAtByEmail(@Param("email") String email);
}
