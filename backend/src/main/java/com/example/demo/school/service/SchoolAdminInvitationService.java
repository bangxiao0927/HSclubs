package com.example.demo.school.service;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.school.mapper.SchoolAdminInvitationMapper;
import com.example.demo.school.mapper.SchoolMapper;
import com.example.demo.school.mapper.SchoolUserMapper;
import com.example.demo.school.model.School;
import com.example.demo.school.model.SchoolAdminInvitation;
import com.example.demo.school.model.SchoolUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SchoolAdminInvitationService {

    private final SchoolAdminInvitationMapper invitationMapper;
    private final SchoolMapper schoolMapper;
    private final SchoolUserMapper schoolUserMapper;
    private final OAuthUserMapper oAuthUserMapper;

    public SchoolAdminInvitationService(SchoolAdminInvitationMapper invitationMapper,
                                        SchoolMapper schoolMapper,
                                        SchoolUserMapper schoolUserMapper,
                                        OAuthUserMapper oAuthUserMapper) {
        this.invitationMapper = invitationMapper;
        this.schoolMapper = schoolMapper;
        this.schoolUserMapper = schoolUserMapper;
        this.oAuthUserMapper = oAuthUserMapper;
    }

    public SchoolAdminInvitation createInvitation(String schoolSlug, String email,
                                                   String invitedByEmail) {
        School school = schoolMapper.findBySlug(schoolSlug);
        if (school == null) {
            throw new IllegalArgumentException("School not found: " + schoolSlug);
        }

        // Check if already has a pending invitation
        SchoolAdminInvitation existing = invitationMapper.findBySchoolAndEmail(school.getId(), email);
        if (existing != null) {
            throw new IllegalArgumentException("A pending invitation already exists for this email.");
        }

        Long invitedByUserId = null;
        if (invitedByEmail != null && !invitedByEmail.isBlank()) {
            invitedByUserId = oAuthUserMapper.findIdByEmail(invitedByEmail);
        }

        SchoolAdminInvitation invitation = new SchoolAdminInvitation();
        invitation.setSchoolId(school.getId());
        invitation.setEmail(email);
        invitation.setRole("school_admin");
        invitation.setStatus("pending");
        invitation.setToken(UUID.randomUUID().toString());
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        invitation.setInvitedByOauthUserId(invitedByUserId);

        invitationMapper.insert(invitation);
        return invitation;
    }

    @Transactional
    public SchoolUser acceptInvitation(String token, String userEmail) {
        SchoolAdminInvitation invitation = invitationMapper.findByToken(token);
        if (invitation == null) {
            throw new IllegalArgumentException("Invalid invitation token.");
        }
        if (!"pending".equalsIgnoreCase(invitation.getStatus())) {
            throw new IllegalArgumentException("This invitation has already been used.");
        }
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This invitation has expired.");
        }

        Long oauthUserId = oAuthUserMapper.findIdByEmail(userEmail);
        if (oauthUserId == null) {
            throw new IllegalStateException("User not found. Please sign in first.");
        }

        // Check if already a member of this school
        SchoolUser existing = schoolUserMapper.findBySchoolAndUser(invitation.getSchoolId(), oauthUserId);
        if (existing != null && "active".equalsIgnoreCase(existing.getStatus())) {
            // Already a member — just mark invitation as accepted
            invitationMapper.updateStatus(invitation.getId(), "accepted");
            return existing;
        }

        // Add user to school
        SchoolUser schoolUser = new SchoolUser();
        schoolUser.setSchoolId(invitation.getSchoolId());
        schoolUser.setOauthUserId(oauthUserId);
        schoolUser.setRole(invitation.getRole());
        schoolUser.setStatus("active");
        schoolUser.setInvitedByOauthUserId(invitation.getInvitedByOauthUserId());
        schoolUserMapper.insert(schoolUser);

        // Mark invitation as accepted
        invitationMapper.updateStatus(invitation.getId(), "accepted");

        return schoolUser;
    }
}
