package com.example.demo.school.model;

import java.time.LocalDateTime;

public class SchoolAdminInvitation {

    private Long id;
    private Long schoolId;
    private String email;
    private String role;
    private String status;
    private String token;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Long invitedByOauthUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getInvitedByOauthUserId() { return invitedByOauthUserId; }
    public void setInvitedByOauthUserId(Long invitedByOauthUserId) { this.invitedByOauthUserId = invitedByOauthUserId; }
}
