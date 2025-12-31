package com.example.demo.auth.controller;

import java.util.List;

import com.example.demo.auth.model.AuthProvider;
import com.example.demo.auth.model.AuthUser;
import com.example.demo.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

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
}
