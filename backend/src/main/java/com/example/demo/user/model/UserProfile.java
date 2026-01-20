package com.example.demo.user.model;

public class UserProfile {

    private Long oauthUserId;
    private Integer graduationYear;

    public Long getOauthUserId() {
        return oauthUserId;
    }

    public void setOauthUserId(Long oauthUserId) {
        this.oauthUserId = oauthUserId;
    }

    public Integer getGraduationYear() {
        return graduationYear;
    }

    public void setGraduationYear(Integer graduationYear) {
        this.graduationYear = graduationYear;
    }
}
