package com.example.demo.club.controller;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.service.ClubService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
    private final SecurityProperties securityProperties;

    public ClubController(ClubService clubService, SecurityProperties securityProperties) {
        this.clubService = clubService;
        this.securityProperties = securityProperties;
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

    @GetMapping("/{clubSlugOrId}")
    public Club get(@PathVariable String clubSlugOrId, Authentication authentication) {
        Club club = resolveClub(clubSlugOrId, resolveViewerEmail(authentication));
        if (club == null) {
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
                       @RequestBody Club club,
                       Authentication authentication) {
        Club existing = requireManageAccess(clubSlugOrId, authentication);
        try {
            return clubService.update(existing.getId(), club);
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

    // ---- Members ----

    @GetMapping("/{clubSlugOrId}/members")
    public List<ClubMemberView> listMembers(@PathVariable String clubSlugOrId,
                                             Authentication authentication) {
        Club club = requireManageAccess(clubSlugOrId, authentication);
        return clubService.findMembers(club.getId());
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
        List<Club> clubs = clubService.findAll();
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
            events.add(event);
        }
        return events;
    }

    // ---- Permission helpers ----

    private Club resolveClub(String clubSlugOrId, String viewerEmail) {
        try {
            Long numericId = Long.valueOf(clubSlugOrId);
            return clubService.findById(numericId, viewerEmail);
        } catch (NumberFormatException e) {
            return clubService.findBySlug(clubSlugOrId, viewerEmail);
        }
    }

    private String resolveViewerEmail(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            return null;
        }
        OAuth2User principal = token.getPrincipal();
        if (principal == null) {
            return null;
        }
        Object email = principal.getAttributes().get("email");
        return (email instanceof String str && !str.isBlank()) ? str : null;
    }

    private boolean isPlatformOwner(Authentication authentication) {
        String email = resolveViewerEmail(authentication);
        if (email == null || securityProperties.getOwnerEmails() == null) {
            return false;
        }
        return securityProperties.getOwnerEmails().stream()
            .filter(item -> item != null && !item.isBlank())
            .anyMatch(item -> email.equalsIgnoreCase(item.trim()));
    }

    private void requirePlatformOwner(Authentication authentication) {
        String email = resolveViewerEmail(authentication);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!isPlatformOwner(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform owner access required");
        }
    }

    private Club requireManageAccess(String clubSlugOrId, Authentication authentication) {
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
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
}
