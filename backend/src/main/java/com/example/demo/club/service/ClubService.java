package com.example.demo.club.service;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.model.ViewerMembershipStatus;
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
    private final OAuthUserMapper oAuthUserMapper;

    public ClubService(ClubMapper clubMapper, OAuthUserMapper oAuthUserMapper) {
        this.clubMapper = clubMapper;
        this.oAuthUserMapper = oAuthUserMapper;
    }

    // ---- Listing ----

    public List<Club> findAll() {
        return clubMapper.findAll();
    }

    public List<Club> findAllPaginated(int page, int size) {
        int offset = Math.max(0, page) * size;
        int limit = Math.max(1, Math.min(size, 100));
        return clubMapper.findAllPaginated(offset, limit);
    }

    public List<Club> search(String name, String category, String alias,
                             String advisor, String query, int page, int size) {
        int limit = Math.max(1, Math.min(size, 100));
        int offset = Math.max(0, page) * limit;
        return clubMapper.search(
            cleanSearchTerm(name),
            cleanSearchTerm(category),
            cleanSearchTerm(alias),
            cleanSearchTerm(advisor),
            cleanSearchTerm(query),
            offset,
            limit
        );
    }

    public int countAll() {
        return clubMapper.countAll();
    }

    // ---- Single club ----

    public Club findById(Long id) {
        return clubMapper.findById(id);
    }

    public Club findById(Long id, String viewerEmail) {
        Club club = clubMapper.findById(id);
        applyViewerPermissions(club, viewerEmail);
        return club;
    }

    public Club findBySlug(String slug, String viewerEmail) {
        Club club = clubMapper.findBySlug(slug);
        applyViewerPermissions(club, viewerEmail);
        return club;
    }

    // ---- Members ----

    public List<ClubMemberView> findMembers(Long clubId) {
        return clubMapper.findMembersByClubId(clubId);
    }

    // ---- CRUD ----

    public Club create(Club club) {
        normalizeClub(club);
        club.setId(null);
        clubMapper.insert(club);
        return club;
    }

    public Club update(Long id, Club club) {
        normalizeClub(club);
        club.setId(id);
        clubMapper.update(club);
        return club;
    }

    public void delete(Long id) {
        clubMapper.delete(id);
    }

    // ---- Membership requests ----

    @Transactional
    public void applyForMembership(Long clubId, String viewerEmail) {
        if (!StringUtils.hasText(viewerEmail)) {
            throw new IllegalArgumentException("Viewer email is required");
        }
        Long oauthUserId = oAuthUserMapper.findIdByEmail(viewerEmail);
        if (oauthUserId == null) {
            throw new IllegalStateException("Viewer is not registered as an OAuth user");
        }
        ClubMembershipRequest existing = clubMapper.findPendingRequestByClubAndUser(clubId, oauthUserId);
        if (existing != null) {
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

    private String cleanSearchTerm(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
