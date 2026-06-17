package com.example.demo.platform.controller;

import com.example.demo.auth.config.SecurityProperties;
import com.example.demo.platform.service.PlatformService;
import com.example.demo.school.model.SchoolAdminInvitation;
import com.example.demo.school.service.SchoolAdminInvitationService;
import com.example.demo.school.model.School;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Map;

@RestController
@RequestMapping("/api/platform")
public class PlatformAdminController {

    private final PlatformService platformService;
    private final SecurityProperties securityProperties;
    private final SchoolAdminInvitationService invitationService;

    public PlatformAdminController(PlatformService platformService,
                                    SecurityProperties securityProperties,
                                    SchoolAdminInvitationService invitationService) {
        this.platformService = platformService;
        this.securityProperties = securityProperties;
        this.invitationService = invitationService;
    }

    @GetMapping("/schools")
    public List<School> listSchools(Authentication authentication) {
        requirePlatformOwner(authentication);
        return platformService.findAll();
    }

    @PostMapping("/schools")
    @ResponseStatus(HttpStatus.CREATED)
    public School createSchool(@RequestBody School school,
                               Authentication authentication) {
        requirePlatformOwner(authentication);
        try {
            return platformService.createSchool(school);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/schools/{slug}")
    public School updateSchool(@PathVariable String slug,
                               @RequestBody School school,
                               Authentication authentication) {
        requirePlatformOwner(authentication);
        try {
            School updated = platformService.updateSchool(slug, school);
            if (updated == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found");
            }
            return updated;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/schools/{slug}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public SchoolAdminInvitation createInvitation(@PathVariable String slug,
                                                   @RequestBody Map<String, String> body,
                                                   Authentication authentication) {
        requirePlatformOwner(authentication);
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        try {
            String inviterEmail = resolveViewerEmail(authentication);
            return invitationService.createInvitation(slug, email.trim(), inviterEmail);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/invitations/{token}/accept")
    public Map<String, Object> acceptInvitation(@PathVariable String token,
                                                 Authentication authentication) {
        String email = resolveViewerEmail(authentication);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            invitationService.acceptInvitation(token, email);
            return Map.of("status", "accepted", "message", "You are now a school admin.");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
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
        Map<String, Object> attributes = principal.getAttributes();
        Object email = attributes != null ? attributes.get("email") : null;
        return (email instanceof String str && !str.isBlank()) ? str : null;
    }

    private boolean isPlatformOwner(Authentication authentication) {
        String email = resolveViewerEmail(authentication);
        if (email == null || securityProperties.getOwnerEmails() == null) {
            return false;
        }
        return securityProperties.getOwnerEmails().stream()
            .filter(StringUtils::hasText)
            .anyMatch(item -> email.equalsIgnoreCase(item.trim()));
    }

    private void requirePlatformOwner(Authentication authentication) {
        if (!isPlatformOwner(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only platform owners can access this resource.");
        }
    }
}
