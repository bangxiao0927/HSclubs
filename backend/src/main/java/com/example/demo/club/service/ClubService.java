package com.example.demo.club.service;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.model.ViewerMembershipStatus;
import com.example.demo.school.mapper.SchoolMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class ClubService {

    private final ClubMapper clubMapper;
    private final SchoolMapper schoolMapper;
    private final OAuthUserMapper oAuthUserMapper;

    public ClubService(ClubMapper clubMapper, SchoolMapper schoolMapper, OAuthUserMapper oAuthUserMapper) {
        this.clubMapper = clubMapper;
        this.schoolMapper = schoolMapper;
        this.oAuthUserMapper = oAuthUserMapper;
    }

    public List<Club> findAll() {
        return clubMapper.findAll();
    }

    public Club findById(Long id) {
        return clubMapper.findById(id);
    }

    public Club findById(Long id, String viewerEmail) {
        Club club = clubMapper.findById(id);
        applyViewerPermissions(club, viewerEmail);
        return club;
    }

    public List<ClubMemberView> findMembers(Long clubId) {
        return clubMapper.findMembersByClubId(clubId);
    }

    public Club create(Club club) {
        ensureSchoolExists(club.getSchoolId());
        normalizeClub(club);
        club.setId(null);
        clubMapper.insert(club);
        return club;
    }

    public Club update(Long id, Club club) {
        ensureSchoolExists(club.getSchoolId());
        normalizeClub(club);
        club.setId(id);
        clubMapper.update(club);
        return club;
    }

    public void delete(Long id) {
        clubMapper.delete(id);
    }

    private void ensureSchoolExists(Long schoolId) {
        if (schoolId == null || schoolMapper.findById(schoolId) == null) {
            throw new IllegalArgumentException("Invalid schoolId");
        }
    }

    private void normalizeClub(Club club) {
        if (club.getAchievements() == null) {
            club.setAchievements(Collections.emptyList());
        }
        if (club.getMemberCount() == null) {
            club.setMemberCount(0);
        }
    }

    private void applyViewerPermissions(Club club, String viewerEmail) {
        if (club == null) {
            return;
        }
        club.setViewerIsMember(false);
        club.setViewerRole(null);
        club.setCanManage(false);
        club.setViewerHasPendingRequest(false);
        if (!StringUtils.hasText(viewerEmail)) {
            return;
        }

        ViewerMembershipStatus membershipStatus = clubMapper.findMembershipStatus(club.getId(), viewerEmail);
        if (membershipStatus != null) {
            boolean isMember = Boolean.TRUE.equals(membershipStatus.getMember());
            club.setViewerIsMember(isMember);
            club.setViewerRole(membershipStatus.getRoleName());
            boolean canManage = membershipStatus.getRoleName() != null &&
                "president".equalsIgnoreCase(membershipStatus.getRoleName().trim());
            club.setCanManage(canManage);
            if (isMember) {
                return;
            }
        }

        Long viewerId = oAuthUserMapper.findIdByEmail(viewerEmail);
        if (viewerId == null) {
            return;
        }

        ClubMembershipRequest pendingRequest = clubMapper.findPendingRequestByClubAndUser(club.getId(), viewerId);
        club.setViewerHasPendingRequest(pendingRequest != null);
    }

    public void applyForMembership(Long clubId, String viewerEmail) {
        if (!StringUtils.hasText(viewerEmail)) {
            throw new IllegalArgumentException("Viewer email is required");
        }
        Long oauthUserId = oAuthUserMapper.findIdByEmail(viewerEmail);
        if (oauthUserId == null) {
            throw new IllegalStateException("Viewer is not registered as an OAuth user");
        }
        ViewerMembershipStatus membershipStatus = clubMapper.findMembershipStatus(clubId, viewerEmail);
        if (membershipStatus != null && Boolean.TRUE.equals(membershipStatus.getMember())) {
            throw new IllegalStateException("You are already a member of this club");
        }
        ClubMembershipRequest pendingRequest = clubMapper.findPendingRequestByClubAndUser(clubId, oauthUserId);
        if (pendingRequest != null) {
            throw new IllegalStateException("You already have a pending request for this club");
        }
        clubMapper.insertMembershipRequest(clubId, oauthUserId);
    }

    public List<ClubMembershipRequest> findPendingRequests(Long clubId) {
        return clubMapper.findPendingRequestsByClubId(clubId);
    }

    @Transactional
    public void approveMembershipRequest(Long clubId, Long requestId) {
        ClubMembershipRequest request = clubMapper.findMembershipRequestById(requestId);
        if (request == null || !clubId.equals(request.getClubId())) {
            throw new IllegalArgumentException("Pending request not found for this club");
        }
        clubMapper.insertMember(request.getClubId(), request.getOauthUserId(), "member");
        clubMapper.deleteMembershipRequest(requestId);
    }

    @Transactional
    public void rejectMembershipRequest(Long clubId, Long requestId) {
        ClubMembershipRequest request = clubMapper.findMembershipRequestById(requestId);
        if (request == null || !clubId.equals(request.getClubId())) {
            throw new IllegalArgumentException("Pending request not found for this club");
        }
        clubMapper.deleteMembershipRequest(requestId);
    }

    public void cancelMembershipRequest(Long clubId, String viewerEmail) {
        if (!StringUtils.hasText(viewerEmail)) {
            throw new IllegalArgumentException("Viewer email is required");
        }
        Long oauthUserId = oAuthUserMapper.findIdByEmail(viewerEmail);
        if (oauthUserId == null) {
            throw new IllegalStateException("Viewer is not registered as an OAuth user");
        }
        ClubMembershipRequest pendingRequest = clubMapper.findPendingRequestByClubAndUser(clubId, oauthUserId);
        if (pendingRequest == null) {
            throw new IllegalArgumentException("You do not have a pending request for this club");
        }
        clubMapper.deleteMembershipRequest(pendingRequest.getId());
    }
}
