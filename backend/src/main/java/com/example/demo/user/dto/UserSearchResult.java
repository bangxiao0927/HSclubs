package com.example.demo.user.dto;

import com.example.demo.auth.model.OAuthUserRecord;

/**
 * Minimal, non-sensitive projection of {@link OAuthUserRecord} returned by the platform-owner
 * member search (used to locate a person to assign as club president). Deliberately excludes
 * {@code provider}, {@code providerUserId}, {@code role} and {@code avatarUrl} since the
 * assignment flow only needs enough information to identify and label a candidate.
 */
public record UserSearchResult(Long id, String email, String displayName) {

    public static UserSearchResult from(OAuthUserRecord record) {
        return new UserSearchResult(record.getId(), record.getEmail(), record.getDisplayName());
    }
}
