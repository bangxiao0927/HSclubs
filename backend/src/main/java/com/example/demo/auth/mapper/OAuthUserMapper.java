package com.example.demo.auth.mapper;

import com.example.demo.auth.model.OAuthUserRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OAuthUserMapper {

    void upsert(OAuthUserRecord record);
}
