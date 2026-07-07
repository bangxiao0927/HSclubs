package com.example.demo.auth.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.auth.model.OAuthUserRecord;

@Service
public class OAuthUserService {

    private final OAuthUserMapper oAuthUserMapper;

    public OAuthUserService(OAuthUserMapper oAuthUserMapper) {
        this.oAuthUserMapper = oAuthUserMapper;
    }

    @Transactional
    public void recordLogin(String provider, Map<String, Object> attributes) {
        if (attributes == null) {
            return;
        }

        String providerUserId = firstNonBlank(attributes, "id", "sub", "email");
        if (providerUserId == null || providerUserId.isBlank()) {
            return;
        }

        OAuthUserRecord record = new OAuthUserRecord();
        record.setProvider(provider);
        record.setProviderUserId(providerUserId);
        record.setEmail(firstNonBlank(attributes, "email"));
        record.setDisplayName(firstNonBlank(attributes, "name", "displayName", "given_name"));
        record.setAvatarUrl(firstNonBlank(attributes, "picture", "avatar", "avatar_url"));
        String role = firstNonBlank(attributes, "role");
        record.setRole(role == null || role.isBlank() ? "student" : role);

        oAuthUserMapper.upsert(record);
    }

    private String firstNonBlank(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            Object value = attributes.get(key);
            if (value instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
    }

    public java.time.LocalDateTime findCreatedAtByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return oAuthUserMapper.findCreatedAtByEmail(email);
    }

    public Long findIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return oAuthUserMapper.findIdByEmail(email);
    }

}
