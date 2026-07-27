package com.example.demo.user.controller;

import com.example.demo.auth.mapper.OAuthUserMapper;
import com.example.demo.club.model.Club;
import com.example.demo.club.model.ClubMembershipRequest;
import com.example.demo.security.AuthenticatedUserResolver;
import com.example.demo.user.dto.UserSearchResult;
import com.example.demo.user.dto.UpdateGraduationYearRequest;
import com.example.demo.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public UserController(UserService userService,
                           OAuthUserMapper oAuthUserMapper,
                           AuthenticatedUserResolver authenticatedUserResolver) {
        this.userService = userService;
        this.oAuthUserMapper = oAuthUserMapper;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PatchMapping("/me/graduation-year")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateGraduationYear(@Valid @RequestBody UpdateGraduationYearRequest request,
                                     Authentication authentication) {
        String email = requireAuthenticatedEmail(authentication);
        try {
            userService.updateGraduationYear(email, request.getGraduationYear());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping("/me/clubs")
    public List<Club> getMyClubs(Authentication authentication) {
        String email = requireAuthenticatedEmail(authentication);
        try {
            return userService.findUserClubs(email);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping("/me/membership-requests")
    public List<ClubMembershipRequest> getMyMembershipRequests(Authentication authentication) {
        String email = requireAuthenticatedEmail(authentication);
        try {
            return userService.findUserPendingRequests(email);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping("/search")
    public List<UserSearchResult> searchUsers(@RequestParam("q") String query,
                                              @RequestParam(defaultValue = "10") int limit,
                                              Authentication authentication) {
        // Only platform owners may enumerate the member directory (used to locate a
        // president candidate); every other authenticated user gets 403.
        authenticatedUserResolver.requirePlatformOwner(authentication);
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must be at least 2 characters");
        }
        int cappedLimit = Math.max(1, Math.min(limit, 20));
        return oAuthUserMapper.searchByEmailOrName(trimmed, cappedLimit).stream()
            .map(UserSearchResult::from)
            .toList();
    }

    private final OAuthUserMapper oAuthUserMapper;

    private String requireAuthenticatedEmail(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        OAuth2User principal = token.getPrincipal();
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        Map<String, Object> attributes = principal.getAttributes();
        Object emailAttribute = attributes.get("email");
        String email = emailAttribute instanceof String str ? str : null;
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authenticated email is required");
        }
        return email;
    }
}
