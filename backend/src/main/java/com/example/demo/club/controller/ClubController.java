package com.example.demo.club.controller;

import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.service.ClubService;
import com.example.demo.club.service.ClubVisibilityPolicy;
import com.example.demo.security.AuthenticatedUserResolver;
import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final ClubVisibilityPolicy clubVisibilityPolicy;

    public ClubController(ClubService clubService,
                          AuthenticatedUserResolver authenticatedUserResolver,
                          ClubVisibilityPolicy clubVisibilityPolicy) {
        this.clubService = clubService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.clubVisibilityPolicy = clubVisibilityPolicy;
    }

    // ---- Club listing & detail ----

    @GetMapping
    public List<Club> list(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "50") int size,
                           @RequestParam(required = false) String name,
                           @RequestParam(required = false) String category,
                           @RequestParam(required = false) String alias,
                           @RequestParam(required = false) String advisor,
                           @RequestParam(name = "q", required = false) String query) {
        if (hasSearchTerm(name, category, alias, advisor, query)) {
            return clubService.search(name, category, alias, advisor, query, page, size);
        }
        return clubService.findAllPaginated(page, size);
    }

    @GetMapping("/count")
    public Map<String, Integer> count() {
        return Map.of("count", clubService.countAll());
    }

    @GetMapping("/{clubSlugOrId}")
    public Club get(@PathVariable String clubSlugOrId, Authentication authentication) {
        Club club = resolveClub(clubSlugOrId, resolveViewerEmail(authentication));
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        // findById/findBySlug carry no status filter, unlike every listing query, so this is the
        // one read path that can surface a club the directory deliberately hides. Same policy
        // and same 404 (never 403) as the post feed and comment list: a hidden club must be
        // indistinguishable from one that does not exist, or the 403 itself confirms it exists.
        if (!clubVisibilityPolicy.isVisibleTo(club, authentication)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        return club;
    }

    // ---- Create / Update / Delete ----

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Club create(@RequestBody Club club, Authentication authentication) {
        requirePlatformOwner(authentication);
        try {
            return clubService.create(club);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/{clubSlugOrId}")
    public Club update(@PathVariable String clubSlugOrId,
                       @RequestBody JsonNode patch,
                       Authentication authentication) {
        Club existing = requireManageAccess(clubSlugOrId, authentication);
        try {
            // The body is taken as raw JSON, not bound into a Club, so the service can tell
            // "field omitted" from "field explicitly set to null": binding into a POJO collapses
            // both into null, which is what made a partial edit wipe every other column.
            return clubService.update(existing.getId(), patch);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{clubSlugOrId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String clubSlugOrId, Authentication authentication) {
        requirePlatformOwner(authentication);
        Club existing = resolveClub(clubSlugOrId, resolveViewerEmail(authentication));
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        clubService.delete(existing.getId());
    }

    // ---- Archive / restore (platform owner only) ----
    //
    // The reversible alternative to DELETE: an archived club keeps its rows, its posts, and its
    // roster, but drops out of every listing, search result, and calendar entry, and its detail
    // endpoint answers 404 for anyone but a member, its president, or a platform owner
    // (ClubVisibilityPolicy). Status is not editable through PUT, so this is the only way to
    // change it -- a club president must not be able to publish or hide their own club.

    @PostMapping("/{clubSlugOrId}/archive")
    public Club archive(@PathVariable String clubSlugOrId, Authentication authentication) {
        return setArchived(clubSlugOrId, authentication, true);
    }

    @DeleteMapping("/{clubSlugOrId}/archive")
    public Club restore(@PathVariable String clubSlugOrId, Authentication authentication) {
        return setArchived(clubSlugOrId, authentication, false);
    }

    private Club setArchived(String clubSlugOrId, Authentication authentication, boolean archived) {
        requirePlatformOwner(authentication);
        Club existing = resolveClub(clubSlugOrId, resolveViewerEmail(authentication));
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        try {
            return archived ? clubService.archive(existing.getId()) : clubService.restore(existing.getId());
        } catch (IllegalArgumentException ex) {
            // The club was resolved a moment ago, so this means it disappeared in between.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            // Restoring something that was never archived (e.g. a pending club).
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    // ---- Members ----

    @GetMapping("/{clubSlugOrId}/members")
    public List<ClubMemberView> listMembers(@PathVariable String clubSlugOrId,
                                             Authentication authentication) {
        Club club = requireManageAccess(clubSlugOrId, authentication);
        return clubService.findMembers(club.getId());
    }

    @PutMapping("/{clubSlugOrId}/members/{oauthUserId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMemberRole(@PathVariable String clubSlugOrId,
                                 @PathVariable Long oauthUserId,
                                 @RequestBody UpdateMemberRoleRequest request,
                                 Authentication authentication) {
        requirePlatformOwner(authentication);
        Club club = resolveClub(clubSlugOrId, resolveViewerEmail(authentication));
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        try {
            clubService.updateMemberRole(club.getId(), oauthUserId, request == null ? null : request.roleName());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    // ---- Membership application ----

    @PostMapping("/{clubSlugOrId}/members/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyToClub(@PathVariable String clubSlugOrId, Authentication authentication) {
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Club club = resolveClub(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        // Same policy and same 404 as the detail endpoint: resolveClub carries no status
        // filter, so without this a caller who guesses an id could apply to a club that is
        // archived or still pending -- landing in a president's request queue for a club the
        // directory says does not exist, and which they cannot even open.
        if (!clubVisibilityPolicy.isVisibleTo(club, authentication)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        try {
            clubService.applyForMembership(club.getId(), viewerEmail);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @DeleteMapping("/{clubSlugOrId}/members/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelApplication(@PathVariable String clubSlugOrId, Authentication authentication) {
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Club club = resolveClub(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        try {
            clubService.cancelMembershipRequest(club.getId(), viewerEmail);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/{clubSlugOrId}/membership-requests")
    public List<ClubMembershipRequest> listMembershipRequests(@PathVariable String clubSlugOrId,
                                                               Authentication authentication) {
        Club club = requireManageAccess(clubSlugOrId, authentication);
        return clubService.findPendingRequests(club.getId());
    }

    @PostMapping("/{clubSlugOrId}/membership-requests/{requestId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveMembershipRequest(@PathVariable String clubSlugOrId,
                                          @PathVariable Long requestId,
                                          Authentication authentication) {
        Club club = requireManageAccess(clubSlugOrId, authentication);
        try {
            clubService.approveMembershipRequest(club.getId(), requestId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @DeleteMapping("/{clubSlugOrId}/membership-requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectMembershipRequest(@PathVariable String clubSlugOrId,
                                         @PathVariable Long requestId,
                                         Authentication authentication) {
        Club club = requireManageAccess(clubSlugOrId, authentication);
        try {
            clubService.rejectMembershipRequest(club.getId(), requestId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    // ---- Recommendations ----

    @GetMapping("/recommendations")
    public List<Club> recommendations(Authentication authentication,
                                       @RequestParam(defaultValue = "8") int limit) {
        String viewerEmail = resolveViewerEmail(authentication);
        int cappedLimit = Math.max(1, Math.min(limit, 20));
        return clubService.getRecommendations(viewerEmail, cappedLimit);
    }

    // ---- President assignment (platform owner only) ----

    @PostMapping("/{clubSlugOrId}/presidents/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignPresident(@PathVariable String clubSlugOrId,
                                 @PathVariable Long userId,
                                 Authentication authentication) {
        requirePlatformOwner(authentication);
        Club club = resolveClub(clubSlugOrId, resolveViewerEmail(authentication));
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        try {
            clubService.assignPresident(club.getId(), userId);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @DeleteMapping("/{clubSlugOrId}/presidents/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePresident(@PathVariable String clubSlugOrId,
                                 @PathVariable Long userId,
                                 Authentication authentication) {
        requirePlatformOwner(authentication);
        Club club = resolveClub(clubSlugOrId, resolveViewerEmail(authentication));
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        clubService.removePresident(club.getId(), userId);
    }

    // ---- Calendar ----

    @GetMapping("/calendar")
    public List<Map<String, Object>> calendar() {
        // findCalendarEntries, not findAll: the response below reads a fixed set of scalar
        // fields, so there is no reason to also select description and the achievements CLOB
        // for every club on a public, unauthenticated endpoint.
        List<Club> clubs = clubService.findCalendarEntries();
        List<Map<String, Object>> events = new ArrayList<>();
        for (Club club : clubs) {
            Map<String, Object> event = new HashMap<>();
            event.put("clubId", club.getId());
            event.put("clubName", club.getName());
            event.put("clubSlug", club.getSlug());
            event.put("category", club.getCategory());
            event.put("meetingSchedule", club.getMeetingSchedule());
            event.put("scheduleNote", club.getScheduleNote());
            event.put("location", club.getLocation());
            event.put("advisor", club.getAdvisor());
            event.put("imageUrl", club.getImageUrl());
            event.put("instagramUrl", club.getInstagramUrl());
            event.put("createdAt", club.getCreatedAt());
            event.put("updatedAt", club.getUpdatedAt());
            events.add(event);
        }
        return events;
    }

    // ---- Permission helpers ----

    private Club resolveClub(String clubSlugOrId, String viewerEmail) {
        return clubService.resolveBySlugOrId(clubSlugOrId, viewerEmail);
    }

    private String resolveViewerEmail(Authentication authentication) {
        return authenticatedUserResolver.resolveEmail(authentication);
    }

    private boolean isPlatformOwner(Authentication authentication) {
        return authenticatedUserResolver.isPlatformOwner(authentication);
    }

    private void requirePlatformOwner(Authentication authentication) {
        authenticatedUserResolver.requirePlatformOwner(authentication);
    }

    private Club requireManageAccess(String clubSlugOrId, Authentication authentication) {
        String viewerEmail = authenticatedUserResolver.requireEmail(authentication);
        Club club = resolveClub(clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        boolean canManage = Boolean.TRUE.equals(club.getCanManage()) || isPlatformOwner(authentication);
        if (!canManage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to member details");
        }
        return club;
    }

    private boolean hasSearchTerm(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return true;
            }
        }
        return false;
    }

    public record UpdateMemberRoleRequest(String roleName) {}
}
