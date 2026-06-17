package com.example.demo.club.service;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.model.ViewerMembershipStatus;
import com.example.demo.school.mapper.SchoolMapper;
import com.example.demo.school.model.School;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class ClubService {

    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
        "STEM & Innovation",
        "Creative Arts & Media",
        "Service & Leadership",
        "Culture & Identity",
        "Wellness & Athletics",
        "Competition & Strategy"
    );

    private final ClubMapper clubMapper;
    private final SchoolMapper schoolMapper;
    private final OAuthUserMapper oAuthUserMapper;

    public ClubService(ClubMapper clubMapper, SchoolMapper schoolMapper, OAuthUserMapper oAuthUserMapper) {
        this.clubMapper = clubMapper;
        this.schoolMapper = schoolMapper;
        this.oAuthUserMapper = oAuthUserMapper;
    }

    // ---- Global (deprecated, kept for backward compatibility) ----

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

    // ---- School-scoped ----

    public List<Club> findAllBySchoolId(Long schoolId) {
        return clubMapper.findAllBySchoolId(schoolId);
    }

    public List<Club> findAllBySchoolIdPaginated(Long schoolId, int page, int size) {
        int offset = Math.max(0, page) * size;
        int limit = Math.max(1, Math.min(size, 100));
        return clubMapper.findAllBySchoolIdPaginated(schoolId, offset, limit);
    }

    public int countBySchoolId(Long schoolId) {
        return clubMapper.countBySchoolId(schoolId);
    }

        public Club findBySchoolAndClubSlug(Long schoolId, String clubSlug, String viewerEmail) {
        Club club = clubMapper.findBySchoolIdAndSlug(schoolId, clubSlug);
        applyViewerPermissions(club, viewerEmail);
        return club;
    }

    public Club findBySchoolAndClubId(Long schoolId, Long clubId, String viewerEmail) {
        Club club = clubMapper.findBySchoolIdAndId(schoolId, clubId);
        applyViewerPermissions(club, viewerEmail);
        return club;
    }

    public Club createInSchool(Long schoolId, Club club) {
        ensureSchoolExists(schoolId);
        club.setSchoolId(schoolId);
        normalizeClub(club);
        club.setId(null);
        club.setStatus(coalesceStatus(club.getStatus()));
        club.setVisibility(coalesceVisibility(club.getVisibility()));
        clubMapper.insert(club);
        return club;
    }

    public Club updateInSchool(Long schoolId, Long clubId, Club club) {
        Club existing = clubMapper.findBySchoolIdAndId(schoolId, clubId);
        if (existing == null) {
            throw new IllegalArgumentException("Club not found in this school");
        }
        club.setSchoolId(schoolId);
        normalizeClub(club);
        club.setId(clubId);
        club.setStatus(coalesceStatus(club.getStatus()));
        club.setVisibility(coalesceVisibility(club.getVisibility()));
        clubMapper.update(club);
        return club;
    }

    public void deleteInSchool(Long schoolId, Long clubId) {
        Club existing = clubMapper.findBySchoolIdAndId(schoolId, clubId);
        if (existing == null) {
            throw new IllegalArgumentException("Club not found in this school");
        }
        clubMapper.delete(clubId);
    }

    // ---- School resolver helpers ----

    public School resolveSchool(String schoolSlug) {
        School school = schoolMapper.findBySlug(schoolSlug);
        if (school == null) {
            throw new IllegalArgumentException("School not found: " + schoolSlug);
        }
        if (!"active".equalsIgnoreCase(school.getStatus())) {
            throw new IllegalArgumentException("School is not active: " + schoolSlug);
        }
        return school;
    }

    // ---- Membership (clubId-based, suitable for both global and school-scoped) ----

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

    public Long resolveOauthUserId(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return oAuthUserMapper.findIdByEmail(email);
    }

    // ---- Internal helpers ----

    private void ensureSchoolExists(Long schoolId) {
        if (schoolId == null || schoolMapper.findById(schoolId) == null) {
            throw new IllegalArgumentException("Invalid schoolId");
        }
    }

    private void normalizeClub(Club club) {
        if (!StringUtils.hasText(club.getCategory())) {
            throw new IllegalArgumentException("Category is required");
        }
        String normalizedCategory = club.getCategory().trim();
        if (!ALLOWED_CATEGORIES.contains(normalizedCategory)) {
            throw new IllegalArgumentException("Invalid category");
        }
        club.setCategory(normalizedCategory);
        if (club.getAchievements() == null) {
            club.setAchievements(Collections.emptyList());
        }
        if (club.getMemberCount() == null) {
            club.setMemberCount(0);
        }
    }

    private String coalesceStatus(String status) {
        return StringUtils.hasText(status) ? status : "active";
    }

    private String coalesceVisibility(String visibility) {
        return StringUtils.hasText(visibility) ? visibility : "public";
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
}
