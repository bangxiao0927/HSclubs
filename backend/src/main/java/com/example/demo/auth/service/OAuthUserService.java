package com.example.demo.auth.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.auth.model.OAuthUserRecord;

@Service
public class OAuthUserService {

    private static final Logger log = LoggerFactory.getLogger(OAuthUserService.class);

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

    /**
     * Re-creates the oauth_users row for a session that outlived it.
     *
     * <p>Sessions last 7 days, so a database restore, a migration, or a manual cleanup can
     * leave a signed-in student with no stored row. That state used to wall them out of
     * registration permanently: /api/auth/me reported acceptedTerms=false forever (nothing to
     * read), POST /api/auth/accept-terms updated no rows, and the graduation-year save
     * answered 404 -- so the terms page's Continue button could never lead anywhere. The
     * session principal still carries everything the login upsert needs, so replay it instead
     * of leaving the account unusable until the cookie expires.
     */
    @Transactional
    public void ensureStoredUser(String provider, Map<String, Object> attributes) {
        if (attributes == null) {
            return;
        }
        String email = firstNonBlank(attributes, "email");
        if (email == null || findIdByEmail(email) != null) {
            return;
        }
        log.warn("Authenticated session has no oauth_users row; re-creating it from the session principal");
        recordLogin(provider, attributes);
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
