package com.example.demo.auth.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private String frontendOrigin;
    private List<String> allowedOrigins = new ArrayList<>();
    private String postLoginRedirectUri;
    private List<String> ownerEmails = new ArrayList<>();

    public String getFrontendOrigin() {
        return frontendOrigin;
    }

    public void setFrontendOrigin(String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null) {
            this.allowedOrigins = new ArrayList<>();
            return;
        }

        this.allowedOrigins = new ArrayList<>(allowedOrigins);
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
        this.ownerEmails = ownerEmails == null ? new ArrayList<>() : new ArrayList<>(ownerEmails);
    }
}
