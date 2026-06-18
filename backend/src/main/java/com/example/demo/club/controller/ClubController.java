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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Legacy global club endpoints — retained for backward compatibility.
 * Prefer {@link SchoolClubController} for new development.
 *
 * @deprecated Use school-scoped {@code /api/schools/{slug}/clubs/...} instead.
 */
@Deprecated
@RestController
@RequestMapping("/api/clubs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ClubController {

    private final ClubService clubService;
    private final SecurityProperties securityProperties;

    public ClubController(ClubService clubService, SecurityProperties securityProperties) {
        this.clubService = clubService;
        this.securityProperties = securityProperties;
    }

    @GetMapping
    public List<Club> list() {
        return clubService.findAll();
    }

    @GetMapping("/{id}")
    public Club get(@PathVariable Long id, Authentication authentication) {
        Club club = clubService.findById(id, resolveViewerEmail(authentication));
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        return club;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Club create(@RequestBody Club club) {
        try {
            return clubService.create(club);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Club update(@PathVariable Long id, @RequestBody Club club, Authentication authentication) {
        requireManageAccess(id, authentication);
        try {
            return clubService.update(id, club);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (clubService.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        clubService.delete(id);
    }

    @GetMapping("/{id}/members")
    public List<ClubMemberView> listMembers(@PathVariable Long id, Authentication authentication) {
        requireManageAccess(id, authentication);
        return clubService.findMembers(id);
    }

    @PostMapping("/{id}/members/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyToClub(@PathVariable Long id, Authentication authentication) {
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (clubService.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        try {
            clubService.applyForMembership(id, viewerEmail);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}/members/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelApplication(@PathVariable Long id, Authentication authentication) {
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (clubService.findById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        try {
            clubService.cancelMembershipRequest(id, viewerEmail);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/{id}/membership-requests")
    public List<ClubMembershipRequest> listMembershipRequests(@PathVariable Long id, Authentication authentication) {
        requireManageAccess(id, authentication);
        return clubService.findPendingRequests(id);
    }

    @PostMapping("/{id}/membership-requests/{requestId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveMembershipRequest(@PathVariable Long id,
                                          @PathVariable Long requestId,
                                          Authentication authentication) {
        requireManageAccess(id, authentication);
        try {
            clubService.approveMembershipRequest(id, requestId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}/membership-requests/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectMembershipRequest(@PathVariable Long id,
                                         @PathVariable Long requestId,
                                         Authentication authentication) {
        requireManageAccess(id, authentication);
        try {
            clubService.rejectMembershipRequest(id, requestId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
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

    private boolean isOwner(Authentication authentication) {
        String email = resolveViewerEmail(authentication);
        if (email == null || securityProperties.getOwnerEmails() == null) {
            return false;
        }
        return securityProperties.getOwnerEmails().stream()
            .filter(item -> item != null && !item.isBlank())
            .anyMatch(item -> email.equalsIgnoreCase(item.trim()));
    }

    private Club requireManageAccess(Long clubId, Authentication authentication) {
        String viewerEmail = resolveViewerEmail(authentication);
        if (viewerEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Club club = clubService.findById(clubId, viewerEmail);
        if (club == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found");
        }
        boolean canManage = Boolean.TRUE.equals(club.getCanManage()) || isOwner(authentication);
        if (!canManage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to member details");
        }
        return club;
    }
}
