package com.example.demo.auth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthUser {

    private String id;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String provider;
    private Integer graduationYear;
    @JsonProperty("isPlatformOwner")
    private boolean platformOwner;
    private SchoolMembership homeSchool;
    private List<SchoolMembership> schoolMemberships;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Integer getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Integer graduationYear) { this.graduationYear = graduationYear; }

    public boolean isPlatformOwner() { return platformOwner; }
    public void setPlatformOwner(boolean platformOwner) { this.platformOwner = platformOwner; }

    public SchoolMembership getHomeSchool() { return homeSchool; }
    public void setHomeSchool(SchoolMembership homeSchool) { this.homeSchool = homeSchool; }

    public List<SchoolMembership> getSchoolMemberships() {
        return schoolMemberships;
    }
    public void setSchoolMemberships(List<SchoolMembership> schoolMemberships) {
        this.schoolMemberships = schoolMemberships;
    }

    public void addSchoolMembership(SchoolMembership membership) {
        if (this.schoolMemberships == null) {
            this.schoolMemberships = new ArrayList<>();
        }
        this.schoolMemberships.add(membership);
    }
}
