package com.example.demo.club.model;

import java.time.LocalDateTime;

/**
 * Represents a pending request from a student who wants to join a club.
 */
public class ClubMembershipRequest {

    private Long id;
    private Long clubId;
    private Long oauthUserId;
    private String displayName;
    private String email;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private String status;
    private LocalDateTime reviewedAt;
    private Long reviewedByOauthUserId;
    private String note;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClubId() {
        return clubId;
    }

    public void setClubId(Long clubId) {
        this.clubId = clubId;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Long getReviewedByOauthUserId() {
        return reviewedByOauthUserId;
    }

    public void setReviewedByOauthUserId(Long reviewedByOauthUserId) {
        this.reviewedByOauthUserId = reviewedByOauthUserId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
