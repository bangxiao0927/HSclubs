package com.example.demo.school.model;

import java.time.LocalDateTime;

public class SchoolUser {

    private Long id;
    private Long schoolId;
    private Long oauthUserId;
    private String role;
    private String status;
    private LocalDateTime joinedAt;
    private Long invitedByOauthUserId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(Long schoolId) {
        this.schoolId = schoolId;
    }

    public Long getOauthUserId() {
        return oauthUserId;
    }

    public void setOauthUserId(Long oauthUserId) {
        this.oauthUserId = oauthUserId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Long getInvitedByOauthUserId() {
        return invitedByOauthUserId;
    }

    public void setInvitedByOauthUserId(Long invitedByOauthUserId) {
        this.invitedByOauthUserId = invitedByOauthUserId;
    }
}
