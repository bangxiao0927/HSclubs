package com.example.demo.auth.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Optional, deployment-owned credentials for an App Review account. */
@ConfigurationProperties(prefix = "app.security.internal-review-account")
public class InternalReviewAccountProperties {

    private String email;
    private String displayName = "App Review";
    private String passwordHash;

    public boolean isConfigured() {
        return StringUtils.hasText(email) && StringUtils.hasText(passwordHash);
    }

    public boolean isPartiallyConfigured() {
        return StringUtils.hasText(email) || StringUtils.hasText(passwordHash);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
