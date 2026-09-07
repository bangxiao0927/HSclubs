package com.example.demo.auth.internal;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Optional, deployment-owned credentials for an App Review account. */
@ConfigurationProperties(prefix = "app.security.internal-review-account")
public class InternalReviewAccountProperties {

    private String email;
    private String displayName = "App Review";
    private String passwordHash;
    private String secondaryEmail;
    private String secondaryDisplayName = "App Review 2";
    private String secondaryPasswordHash;

    public boolean isConfigured() {
        return isPrimaryConfigured() || isSecondaryConfigured();
    }

    public boolean isPartiallyConfigured() {
        return isPrimaryPartiallyConfigured() || isSecondaryPartiallyConfigured();
    }

    public boolean isPrimaryConfigured() {
        return StringUtils.hasText(email) && StringUtils.hasText(passwordHash);
    }

    public boolean isPrimaryPartiallyConfigured() {
        return StringUtils.hasText(email) || StringUtils.hasText(passwordHash);
    }

    public boolean isSecondaryConfigured() {
        return StringUtils.hasText(secondaryEmail) && StringUtils.hasText(secondaryPasswordHash);
    }

    public boolean isSecondaryPartiallyConfigured() {
        return StringUtils.hasText(secondaryEmail) || StringUtils.hasText(secondaryPasswordHash);
    }

    public List<Account> configuredAccounts() {
        List<Account> accounts = new ArrayList<>();
        if (isPrimaryConfigured()) {
            accounts.add(new Account(email, displayName, passwordHash));
        }
        if (isSecondaryConfigured()) {
            accounts.add(new Account(secondaryEmail, secondaryDisplayName, secondaryPasswordHash));
        }
        return List.copyOf(accounts);
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

    public String getSecondaryEmail() {
        return secondaryEmail;
    }

    public void setSecondaryEmail(String secondaryEmail) {
        this.secondaryEmail = secondaryEmail;
    }

    public String getSecondaryDisplayName() {
        return secondaryDisplayName;
    }

    public void setSecondaryDisplayName(String secondaryDisplayName) {
        this.secondaryDisplayName = secondaryDisplayName;
    }

    public String getSecondaryPasswordHash() {
        return secondaryPasswordHash;
    }

    public void setSecondaryPasswordHash(String secondaryPasswordHash) {
        this.secondaryPasswordHash = secondaryPasswordHash;
    }

    public record Account(String email, String displayName, String passwordHash) {
    }
}
