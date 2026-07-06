package com.example.demo.auth.mapper;

import com.example.demo.auth.model.OAuthUserRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OAuthUserMapper {

    void upsert(OAuthUserRecord record);

    Long findIdByEmail(@Param("email") String email);

    int acceptTerms(@Param("email") String email);

    Boolean hasAcceptedTerms(@Param("email") String email);
}
