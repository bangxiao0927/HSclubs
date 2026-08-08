package com.example.demo.auth.controller;

import java.util.List;
import java.util.Map;

import com.example.demo.auth.model.AuthProvider;
import com.example.demo.auth.model.AuthUser;
import com.example.demo.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/providers")
    public List<AuthProvider> getProviders() {
        return authService.getProviders();
    }

    @GetMapping("/me")
    public AuthUser getAuthenticatedUser(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2AuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        AuthUser currentUser = authService.getAuthenticatedUser(oauth2AuthenticationToken);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        return currentUser;
    }

    @PostMapping("/accept-terms")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptTerms(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token) || resolveEmail(authentication) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        // A "success" that recorded nothing is worse than an error here: the SPA immediately
        // re-reads /api/auth/me, still sees acceptedTerms=false, and re-routes the student
        // back to the very page they just submitted -- a silent dead end with no error shown.
        // Fail loudly instead so the frontend can surface something actionable.
        boolean accepted;
        try {
            accepted = authService.acceptTerms(token);
        } catch (OAuth2AuthenticationException ex) {
            // The session belongs to an account this school's sign-in rules no longer allow.
            // Left to propagate it would be resolved by the security entry point as a bodyless
            // 401, so the student would just be signed out with no idea why.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getError().getDescription());
        }
        if (!accepted) {
            log.warn("Terms acceptance could not be recorded for the current session");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Terms acceptance could not be recorded, please try again");
        }
    }

    @GetMapping("/me/has-accepted-terms")
    public Map<String, Boolean> hasAcceptedTerms(Authentication authentication) {
        String email = resolveEmail(authentication);
        boolean accepted = email != null && authService.hasAcceptedTerms(email);
        return Map.of("accepted", accepted);
    }

    private String resolveEmail(Authentication authentication) {
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
}
