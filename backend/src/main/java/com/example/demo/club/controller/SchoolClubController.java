package com.example.demo.club.controller;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMemberView;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.club.service.ClubService;
import com.example.demo.school.model.School;
import com.example.demo.school.service.SchoolUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/schools/{schoolSlug}/clubs")
public class SchoolClubController {

    private final ClubService clubService;
    private final SchoolUserService schoolUserService;
    private final SecurityProperties securityProperties;

    public SchoolClubController(ClubService clubService,
                                SchoolUserService schoolUserService,
                                SecurityProperties securityProperties) {
        this.clubService = clubService;
        this.schoolUserService = schoolUserService;
        this.securityProperties = securityProperties;
    }

    // ---- Club listing & detail ----

    @GetMapping
    public List<Club> list(@PathVariable String schoolSlug,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "50") int size) {
        School school = resolveSchoolSafe(schoolSlug);
        return clubService.findAllBySchoolIdPaginated(school.getId(), page, size);
    }

    @GetMapping("/{clubSlugOrId}")
    public Club get(@PathVariable String schoolSlug,
                    @PathVariable String clubSlugOrId,
                    Authentication authentication) {
        School school = resolveSchoolSafe(schoolSlug);
        Club club = resolveClub(school.getId(), clubSlugOrId, resolveViewerEmail(authentication));
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        return club;
    }

    // ---- Create / Update / Delete ----

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Club create(@PathVariable String schoolSlug,
                       @RequestBody Club club,
                       Authentication authentication) {
        requireSchoolAdmin(schoolSlug, authentication);
        School school = resolveSchoolSafe(schoolSlug);
        try {
            return clubService.createInSchool(school.getId(), club);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/{clubSlugOrId}")
    public Club update(@PathVariable String schoolSlug,
                       @PathVariable String clubSlugOrId,
                       @RequestBody Club club,
                       Authentication authentication) {
        requireManageAccess(schoolSlug, clubSlugOrId, authentication);
        School school = resolveSchoolSafe(schoolSlug);
        Club existing = resolveClub(school.getId(), clubSlugOrId, null);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        try {
            return clubService.updateInSchool(school.getId(), existing.getId(), club);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{clubSlugOrId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String schoolSlug,
                       @PathVariable String clubSlugOrId,
                       Authentication authentication) {
        requireSchoolAdmin(schoolSlug, authentication);
        School school = resolveSchoolSafe(schoolSlug);
        Club existing = resolveClub(school.getId(), clubSlugOrId, null);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        clubService.deleteInSchool(school.getId(), existing.getId());
    }

    // ---- Members ----

    @GetMapping("/{clubSlugOrId}/members")
    public List<ClubMemberView> listMembers(@PathVariable String schoolSlug,
                                            @PathVariable String clubSlugOrId,
                                            Authentication authentication) {
        Club club = requireManageAccess(schoolSlug, clubSlugOrId, authentication);
        return clubService.findMembers(club.getId());
    }

    // ---- Membership applications ----

    @PostMapping("/{clubSlugOrId}/members/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyToClub(@PathVariable String schoolSlug,
                            @PathVariable String clubSlugOrId,
                            Authentication authentication) {
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        School school = resolveSchoolSafe(schoolSlug);
        Club club = resolveClub(school.getId(), clubSlugOrId, viewerEmail);
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
    public void cancelApplication(@PathVariable String schoolSlug,
                                  @PathVariable String clubSlugOrId,
                                  Authentication authentication) {
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        School school = resolveSchoolSafe(schoolSlug);
        Club club = resolveClub(school.getId(), clubSlugOrId, viewerEmail);
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

    // ---- Membership request management ----

    @GetMapping("/{clubSlugOrId}/membership-requests")
    public List<ClubMembershipRequest> listMembershipRequests(@PathVariable String schoolSlug,
                                                              @PathVariable String clubSlugOrId,
                                                              Authentication authentication) {
        Club club = requireManageAccess(schoolSlug, clubSlugOrId, authentication);
        return clubService.findPendingRequests(club.getId());
    }

    @PostMapping("/{clubSlugOrId}/membership-requests/{requestId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveMembershipRequest(@PathVariable String schoolSlug,
                                          @PathVariable String clubSlugOrId,
                                          @PathVariable Long requestId,
                                          Authentication authentication) {
        Club club = requireManageAccess(schoolSlug, clubSlugOrId, authentication);
        try {
            clubService.approveMembershipRequest(club.getId(), requestId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @DeleteMapping("/{clubSlugOrId}/membership-requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectMembershipRequest(@PathVariable String schoolSlug,
                                         @PathVariable String clubSlugOrId,
                                         @PathVariable Long requestId,
                                         Authentication authentication) {
        Club club = requireManageAccess(schoolSlug, clubSlugOrId, authentication);
        try {
            clubService.rejectMembershipRequest(club.getId(), requestId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }


    private School resolveSchoolSafe(String schoolSlug) {
        try {
            return clubService.resolveSchool(schoolSlug);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    // ---- Permission helpers ----

    private Club resolveClub(Long schoolId, String clubSlugOrId, String viewerEmail) {
        try {
            Long numericId = Long.valueOf(clubSlugOrId);
            return clubService.findBySchoolAndClubId(schoolId, numericId, viewerEmail);
        } catch (NumberFormatException e) {
            return clubService.findBySchoolAndClubSlug(schoolId, clubSlugOrId, viewerEmail);
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

    private boolean isSchoolAdmin(String schoolSlug, Authentication authentication) {
        String email = resolveViewerEmail(authentication);
        if (email == null) {
            return false;
        }
        School school = resolveSchoolSafe(schoolSlug);
        if (school == null) {
            return false;
        }
        Long oauthUserId = resolveOauthUserId(email);
        if (oauthUserId == null) {
            return false;
        }
        return schoolUserService.isSchoolAdmin(school.getId(), oauthUserId);
    }

    private void requireSchoolAdmin(String schoolSlug, Authentication authentication) {
        if (isPlatformOwner(authentication)) {
            return;
        }
        if (!isSchoolAdmin(schoolSlug, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You do not have permission to manage this school's clubs.");
        }
    }

    private Club requireManageAccess(String schoolSlug,
                                      String clubSlugOrId,
                                      Authentication authentication) {
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        School school = resolveSchoolSafe(schoolSlug);
        Club club = resolveClub(school.getId(), clubSlugOrId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        boolean canManage = Boolean.TRUE.equals(club.getCanManage())
            || isPlatformOwner(authentication)
            || isSchoolAdmin(schoolSlug, authentication);
        if (!canManage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You do not have access to member details");
        }
        return club;
    }

    private Long resolveOauthUserId(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        // Use ClubService's oAuthUserMapper — we'll add a helper
        return clubService.resolveOauthUserId(email);
    }
}
