package com.example.demo.auth.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    private String postLoginRedirectUri = "http://localhost:5173/auth/callback";
    private List<String> ownerEmails = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String getPostLoginRedirectUri() {
        return postLoginRedirectUri;
    }

    public void setPostLoginRedirectUri(String postLoginRedirectUri) {
        this.postLoginRedirectUri = postLoginRedirectUri;
    }

    public List<String> getOwnerEmails() {
        return ownerEmails;
    }

    public void setOwnerEmails(List<String> ownerEmails) {
        this.ownerEmails = ownerEmails == null ? new ArrayList<>() : ownerEmails;
    }
}
