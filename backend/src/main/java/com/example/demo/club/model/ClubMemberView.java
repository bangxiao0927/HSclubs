package com.example.demo.club.model;

import java.time.LocalDateTime;

/**
 * Projection used for exposing club members to management tooling.
 */
public class ClubMemberView {

    private Long oauthUserId;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String roleName;
    private LocalDateTime joinedAt;

    public Long getOauthUserId() {
        return oauthUserId;
    }

    public void setOauthUserId(Long oauthUserId) {
        this.oauthUserId = oauthUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
