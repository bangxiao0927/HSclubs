package com.example.demo.club.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents an extracurricular club that students can explore or join.
 */
public class Club {

    private Long id;
    private String name;
    private String slug;
    private String aliasName;
    private String description;
    private String category;
    private String meetingSchedule;
    private String scheduleNote;
    private String location;
    private String contactEmail;
    private String advisor;
    private String imageUrl;
    private String instagramUrl;
    private Integer memberCount;
    private List<String> achievements;
    private String status;
    private String visibility;
    private LocalDateTime approvedAt;
    private Long approvedByOauthUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String viewerRole;
    private Boolean viewerIsMember;
    private Boolean canManage;
    private Boolean viewerHasPendingRequest;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMeetingSchedule() {
        return meetingSchedule;
    }

    public void setMeetingSchedule(String meetingSchedule) {
        this.meetingSchedule = meetingSchedule;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getScheduleNote() {
        return scheduleNote;
    }

    public void setScheduleNote(String scheduleNote) {
        this.scheduleNote = scheduleNote;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getAdvisor() {
        return advisor;
    }

    public void setAdvisor(String advisor) {
        this.advisor = advisor;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }

    public List<String> getAchievements() {
        return achievements;
    }

    public void setAchievements(List<String> achievements) {
        this.achievements = achievements;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Long getApprovedByOauthUserId() {
        return approvedByOauthUserId;
    }

    public void setApprovedByOauthUserId(Long approvedByOauthUserId) {
        this.approvedByOauthUserId = approvedByOauthUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getViewerRole() {
        return viewerRole;
    }

    public void setViewerRole(String viewerRole) {
        this.viewerRole = viewerRole;
    }

    public Boolean getViewerIsMember() {
        return viewerIsMember;
    }

    public void setViewerIsMember(Boolean viewerIsMember) {
        this.viewerIsMember = viewerIsMember;
    }

    public Boolean getCanManage() {
        return canManage;
    }

    public void setCanManage(Boolean canManage) {
        this.canManage = canManage;
    }

    public Boolean getViewerHasPendingRequest() {
        return viewerHasPendingRequest;
    }

    public void setViewerHasPendingRequest(Boolean viewerHasPendingRequest) {
        this.viewerHasPendingRequest = viewerHasPendingRequest;
    }
}
