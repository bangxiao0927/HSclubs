package com.example.demo.club.model;

/**
 * Captures whether the currently authenticated viewer belongs to a club and any assigned role.
 */
public class ViewerMembershipStatus {

    private Boolean member;
    private String roleName;

    public Boolean getMember() {
        return member;
    }

    public void setMember(Boolean member) {
        this.member = member;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
