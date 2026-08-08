package com.example.demo.club.service;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.model.ViewerMembershipStatus;
import com.example.demo.common.PaginationClamps;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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
    private static final Set<String> ALLOWED_MEMBER_ROLES = Set.of("member", "president");

    // One wording for both ways the same conflict is detected: the check below, and the unique
    // constraint that catches whatever slips past it.
    private static final String DUPLICATE_REQUEST_MESSAGE = "You already have a pending request for this club";

    // A search box, not a query language: ten words is far past anything a student types, and
    // bounds how many OR groups one statement can grow.
    private static final int MAX_SEARCH_TOKENS = 10;

    // The only two club statuses this application writes. 'active' is what every listing query
    // filters on (ActiveClause) and what ClubVisibilityPolicy treats as publicly readable;
    // 'archived' is the reversible alternative to deleting a club outright.
    static final String ACTIVE_STATUS = "active";
    static final String ARCHIVED_STATUS = "archived";

    // Exactly the fields a club manager may edit through PUT /api/clubs/{id}. Everything else
    // the update statement writes -- slug (the club's public URL), status, visibility,
    // approved_at, approved_by_oauth_user_id -- is carried over from the stored row instead:
    // those decide whether a club is listed and published at all, and a club president must not
    // be able to change them by adding a key to their edit request. image_url is absent for the
    // same reason it is absent from the SQL: it only ever changes through the upload flow.
    private static final Set<String> MANAGER_EDITABLE_FIELDS = Set.of(
        "name",
        "aliasName",
        "description",
        "category",
        "meetingSchedule",
        "scheduleNote",
        "location",
        "contactEmail",
        "advisor",
        "achievements"
    );

    private final ClubMapper clubMapper;
    private final OAuthUserMapper oAuthUserMapper;
    private final ObjectMapper objectMapper;

    public ClubService(ClubMapper clubMapper, OAuthUserMapper oAuthUserMapper, ObjectMapper objectMapper) {
        this.clubMapper = clubMapper;
        this.oAuthUserMapper = oAuthUserMapper;
        this.objectMapper = objectMapper;
    }

    // ---- Listing ----

    public List<Club> findAll() {
        return clubMapper.findAll();
    }

    /** Active clubs carrying only the columns the calendar renders. */
    public List<Club> findCalendarEntries() {
        return clubMapper.findCalendarEntries();
    }

    public List<Club> findAllPaginated(int page, int size) {
        int limit = PaginationClamps.clampPageSize(size);
        int offset = PaginationClamps.clampOffset(page, limit);
        return clubMapper.findAllPaginated(offset, limit);
    }

    public List<Club> search(String name, String category, String alias,
                             String advisor, String query, int page, int size) {
        int limit = PaginationClamps.clampPageSize(size);
        int offset = PaginationClamps.clampOffset(page, limit);
        return clubMapper.search(
            cleanSearchTerm(name),
            cleanSearchTerm(category),
            cleanSearchTerm(alias),
            cleanSearchTerm(advisor),
            searchTokens(query),
            offset,
            limit
        );
    }

    // Splits the free-text query into words so each can match a different field, the same way
    // the frontend's own filter does (frontend/src/utils/clubSearch.ts), so an in-memory result
    // set and a server-side one read a student's query the same way. The one deliberate
    // difference is that the frontend also searches a club's Instagram URL, which is not a
    // column on clubs. The cap keeps a pathological query from generating an unbounded number
    // of OR groups in one statement.
    private static List<String> searchTokens(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        return Arrays.stream(query.trim().split("\\s+"))
            .filter(StringUtils::hasText)
            .distinct()
            .limit(MAX_SEARCH_TOKENS)
            .toList();
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

    // The one place every controller with a {clubSlugOrId} path variable resolves it: a purely
    // numeric segment is looked up by id, anything else by slug. Returns null, like findById()
    // and findBySlug() themselves, when neither lookup finds a club -- callers decide how to
    // report that (404, permission checks, etc.).
    public Club resolveBySlugOrId(String clubSlugOrId, String viewerEmail) {
        try {
            Long numericId = Long.valueOf(clubSlugOrId);
            return findById(numericId, viewerEmail);
        } catch (NumberFormatException e) {
            return findBySlug(clubSlugOrId, viewerEmail);
        }
    }

    // ---- Members ----

    public List<ClubMemberView> findMembers(Long clubId) {
        return clubMapper.findMembersByClubId(clubId);
    }

    @Transactional
    public void updateMemberRole(Long clubId, Long oauthUserId, String roleName) {
        String normalizedRole = normalizeMemberRole(roleName);
        int updated = clubMapper.updateMemberRole(clubId, oauthUserId, normalizedRole);
        if (updated == 0) {
            throw new IllegalArgumentException("Member not found for this club");
        }
        if ("president".equals(normalizedRole)) {
            clubMapper.demoteOtherPresidents(clubId, oauthUserId);
        }
    }

    // ---- CRUD ----

    public Club create(Club club) {
        normalizeClub(club);
        club.setId(null);
        clubMapper.insert(club);
        // member_count (and instagram_url) are derived columns that BaseColumnList computes
        // with correlated subqueries rather than reading back from the row this insert just
        // wrote (see ClubMapper.xml's insert -- it never writes member_count at all), so the
        // in-memory `club` the caller passed in is stale for those fields. Re-fetch to hand
        // back what a subsequent GET would actually return.
        return clubMapper.findById(club.getId());
    }

    /**
     * Applies a manager's edit to a club. The patch is raw request JSON so that an omitted field
     * keeps its stored value while an explicitly null field clears it -- the update statement
     * writes every column, so binding the body into a Club first (where both cases look like
     * null) meant a partial edit silently wiped description, advisor, schedule, and the rest.
     * Fields outside {@link #MANAGER_EDITABLE_FIELDS} are ignored, whatever the caller sends.
     */
    public Club update(Long id, JsonNode patch) {
        // Re-read the row here rather than trusting a club the caller already loaded: what this
        // method writes back is the whole row, so it must be merged onto the stored values as
        // this service sees them, not onto an instance a controller may have mutated (the
        // controller's copy carries viewer/permission fields) or held across another write.
        Club existing = clubMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Club not found");
        }
        Club merged = applyEditableFields(existing, patch);
        normalizeClub(merged);
        merged.setId(id);
        clubMapper.update(merged);
        // Same reasoning as create(): member_count is never written by this update, so
        // re-fetch instead of returning the now-stale in-memory club.
        return clubMapper.findById(id);
    }

    private Club applyEditableFields(Club existing, JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        ObjectNode editable = objectMapper.createObjectNode();
        patch.properties().stream()
            .filter(field -> MANAGER_EDITABLE_FIELDS.contains(field.getKey()))
            .forEach(field -> editable.set(field.getKey(), field.getValue()));
        try {
            // readerForUpdating applies only the properties present in the tree, so an omitted
            // key leaves the stored value untouched and an explicit null still clears it.
            return objectMapper.readerForUpdating(existing).readValue(editable);
        } catch (JacksonException e) {
            // A wrong JSON type for a field (e.g. "achievements": 5) is a bad request, not a 500.
            throw new IllegalArgumentException("Invalid club field value", e);
        }
    }

    // The only path allowed to change a club's stored image URL: called from the dedicated,
    // authenticated upload flow in ClubImageController, never from the general club-editing
    // update() above. update() intentionally does not write image_url at all (see
    // ClubMapper.xml), so a caller-supplied imageUrl in an ordinary PUT body can never reach
    // the database through it.
    public Club updateImageUrl(Long id, String imageUrl) {
        clubMapper.updateImageUrl(id, imageUrl);
        return clubMapper.findById(id);
    }

    /**
     * Archives a club: it disappears from every listing, search result, calendar entry, and the
     * public detail endpoint, but keeps its rows so the decision is reversible with
     * {@link #restore(Long)} -- unlike delete(), which removes the club outright.
     *
     * <p>Platform-owner only, enforced by the controller. Status is written through its own
     * column-scoped statement rather than the general update(), which a club president can
     * reach: a president must not be able to publish or hide their own club.
     */
    public Club archive(Long id) {
        if (clubMapper.updateStatus(id, ARCHIVED_STATUS) == 0) {
            throw new IllegalArgumentException("Club not found");
        }
        return clubMapper.findById(id);
    }

    /**
     * Puts an archived club back into the directory. Deliberately refuses any other non-active
     * status: no previous status is recorded anywhere, so activating (say) a pending club here
     * would publish something that was never approved and lose that state for good.
     */
    public Club restore(Long id) {
        Club existing = clubMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Club not found");
        }
        if (!ARCHIVED_STATUS.equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalStateException("Only an archived club can be restored");
        }
        clubMapper.updateStatus(id, ACTIVE_STATUS);
        return clubMapper.findById(id);
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
        // Applying while already in the club is what let an approval overwrite the applicant's
        // role; reject it here so the request never exists in the first place.
        ViewerMembershipStatus membership = clubMapper.findMembershipStatusByUserId(clubId, oauthUserId);
        if (membership != null && Boolean.TRUE.equals(membership.getMember())) {
            throw new IllegalStateException("You are already a member of this club");
        }
        ClubMembershipRequest existing = clubMapper.findPendingRequestByClubAndUser(clubId, oauthUserId);
        if (existing != null) {
            throw new IllegalStateException(DUPLICATE_REQUEST_MESSAGE);
        }
        try {
            clubMapper.insertMembershipRequest(clubId, oauthUserId);
        } catch (DuplicateKeyException e) {
            // The check above is check-then-insert, so two concurrent requests (a double-clicked
            // button, two tabs) both pass it and the second violates
            // uq_club_membership_request. That is the same conflict the check reports, so it
            // leaves this service as the same exception rather than as a 500.
            throw new IllegalStateException(DUPLICATE_REQUEST_MESSAGE, e);
        }
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
        // Never insertMember here: that statement overwrites role_name, so approving a request
        // from someone already in the club (the president, most damagingly) demoted them.
        clubMapper.insertMemberIfAbsent(request.getClubId(), request.getOauthUserId(), "member");
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

    // ---- Recommendations ----

    /**
     * Recommend clubs based on categories the user already belongs to.
     * Falls back to popular clubs (highest member count) for cold start.
     */
    public List<Club> getRecommendations(String viewerEmail, int limit) {
        if (viewerEmail != null && !viewerEmail.isBlank()) {
            Long userId = oAuthUserMapper.findIdByEmail(viewerEmail);
            if (userId != null) {
                // Find categories the user is already a member of
                List<String> userCategories = clubMapper.findCategoriesByOauthUserId(userId);
                if (!userCategories.isEmpty()) {
                    List<Long> joinedClubIds = clubMapper.findClubIdsByOauthUserId(userId);
                    // Filtering, ordering and limiting all happen in SQL: this endpoint is
                    // public and returns at most 20 rows, so reading every club to sort and
                    // truncate in Java made the work unbounded in table size for a
                    // constant-size answer.
                    List<Club> personalized =
                        clubMapper.findPopularInCategories(userCategories, joinedClubIds, limit);
                    if (!personalized.isEmpty()) {
                        return personalized;
                    }
                    // Fall through to popular clubs if no unjoined clubs in user's categories
                }
            }
        }

        // Cold start: return most popular clubs
        return clubMapper.findPopular(limit);
    }

    // ---- President management ----

    @Transactional
    public void assignPresident(Long clubId, Long oauthUserId) {
        // Check if already a member
        ViewerMembershipStatus status = clubMapper.findMembershipStatusByUserId(clubId, oauthUserId);
        if (status != null && Boolean.TRUE.equals(status.getMember())) {
            updateMemberRole(clubId, oauthUserId, "president");
        } else {
            clubMapper.insertMember(clubId, oauthUserId, "president");
            clubMapper.demoteOtherPresidents(clubId, oauthUserId);
        }
    }

    @Transactional
    public void removePresident(Long clubId, Long oauthUserId) {
        ViewerMembershipStatus status = clubMapper.findMembershipStatusByUserId(clubId, oauthUserId);
        if (status == null || !"president".equalsIgnoreCase(status.getRoleName())) {
            throw new IllegalArgumentException("User is not a president of this club");
        }
        // Downgrade to regular member
        clubMapper.updateMemberRole(clubId, oauthUserId, "member");
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
        club.setLocation(ClubLocationNormalizer.normalize(club.getLocation()));
        if (club.getAchievements() == null) {
            club.setAchievements(Collections.emptyList());
        }
        if (!StringUtils.hasText(club.getStatus())) {
            club.setStatus(ACTIVE_STATUS);
        }
        if (!StringUtils.hasText(club.getVisibility())) {
            club.setVisibility("public");
        }
    }

    private String cleanSearchTerm(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeMemberRole(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            throw new IllegalArgumentException("Member role is required");
        }
        String normalizedRole = roleName.trim().toLowerCase();
        if (!ALLOWED_MEMBER_ROLES.contains(normalizedRole)) {
            throw new IllegalArgumentException("Invalid member role");
        }
        return normalizedRole;
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
