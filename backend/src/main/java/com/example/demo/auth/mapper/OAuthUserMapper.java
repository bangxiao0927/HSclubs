package com.example.demo.auth.mapper;

import com.example.demo.auth.model.OAuthUserRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OAuthUserMapper {

    void upsert(OAuthUserRecord record);

    Long findIdByEmail(@Param("email") String email);

    List<OAuthUserRecord> searchByEmailOrName(@Param("query") String query, @Param("limit") int limit);

    OAuthUserRecord findByUid(@Param("uid") Long uid);

    int acceptTerms(@Param("email") String email);

    Boolean hasAcceptedTerms(@Param("email") String email);
}
