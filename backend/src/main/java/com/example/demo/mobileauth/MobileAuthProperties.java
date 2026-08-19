package com.example.demo.mobileauth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the mobile authentication flow (issue HSclubs#140).
 *
 * <p>The callback allow-list is the security pivot of the {@code start} step: the app asks the
 * school to send the browser to a {@code redirect_uri}, and only a value on this list is honoured,
 * so a tampered link cannot redirect the one-time code anywhere else. It defaults to the one
 * official Universal Link on the guiding page's domain.
 *
 * <p>The code lifetime is bounded to the contract's 60-120 seconds; a value outside that range is
 * clamped rather than trusted, because a long-lived one-time code is the thing this flow exists to
 * avoid.
 */
@ConfigurationProperties(prefix = "app.mobile-auth")
public class MobileAuthProperties {

    private List<String> callbackUrls = List.of("https://clubs.bangxiao.net/mobile-auth/callback");
    private long codeTtlSeconds = 90;

    public List<String> getCallbackUrls() {
        return callbackUrls;
    }

    public void setCallbackUrls(List<String> callbackUrls) {
        this.callbackUrls = callbackUrls == null ? List.of() : List.copyOf(callbackUrls);
    }

    public long getCodeTtlSeconds() {
        return codeTtlSeconds;
    }

    public void setCodeTtlSeconds(long codeTtlSeconds) {
        this.codeTtlSeconds = codeTtlSeconds;
    }

    /** The lifetime actually used, clamped to the contract's 60-120 second window. */
    public long resolvedCodeTtlSeconds() {
        return Math.min(120, Math.max(60, codeTtlSeconds));
    }

    public boolean isRegisteredCallback(String redirectUri) {
        return redirectUri != null && callbackUrls.contains(redirectUri);
    }

    /** Whether this deployment can offer mobile auth at all: it needs at least one callback. */
    public boolean isConfigured() {
        return !callbackUrls.isEmpty();
    }

    public String primaryCallbackUrl() {
        return callbackUrls.isEmpty() ? null : callbackUrls.get(0);
    }
}
