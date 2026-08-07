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
    private String authorizationRequestBaseUri;
    private Csrf csrf = new Csrf();
    private Login login = new Login();

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

    public String getAuthorizationRequestBaseUri() {
        return authorizationRequestBaseUri;
    }

    public void setAuthorizationRequestBaseUri(String authorizationRequestBaseUri) {
        this.authorizationRequestBaseUri = authorizationRequestBaseUri;
    }

    public Csrf getCsrf() {
        return csrf;
    }

    public void setCsrf(Csrf csrf) {
        this.csrf = csrf == null ? new Csrf() : csrf;
    }

    public Login getLogin() {
        return login;
    }

    public void setLogin(Login login) {
        this.login = login == null ? new Login() : login;
    }

    /**
     * Server-side CSRF token enforcement (app.security.csrf.enabled), separate from and in
     * addition to the SameSite=Lax session cookie. Defaults to false so this can ship without
     * breaking the frontend's existing fetch() calls, which don't yet send the token back;
     * flip it on once the frontend reads the XSRF-TOKEN cookie and sends X-XSRF-TOKEN.
     */
    public static class Csrf {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Who is allowed to sign in at all (app.security.login.*). Both restrictions are off by
     * default: this repo is meant to be copied by any school, and a default that silently
     * rejected accounts would be a confusing first run. A school that wants its site limited to
     * its own Google Workspace turns them on in configuration, with no code change.
     */
    public static class Login {

        /**
         * Email domains allowed to sign in, e.g. {@code students.example.edu}. Empty (the
         * default) means any account the OAuth provider authenticates may sign in.
         */
        private List<String> allowedEmailDomains = new ArrayList<>();

        /**
         * Whether the provider must report the email address as verified. Off by default so a
         * provider that does not send the claim at all cannot lock everyone out.
         */
        private boolean requireVerifiedEmail = false;

        public List<String> getAllowedEmailDomains() {
            return allowedEmailDomains;
        }

        public void setAllowedEmailDomains(List<String> allowedEmailDomains) {
            this.allowedEmailDomains =
                allowedEmailDomains == null ? new ArrayList<>() : new ArrayList<>(allowedEmailDomains);
        }

        public boolean isRequireVerifiedEmail() {
            return requireVerifiedEmail;
        }

        public void setRequireVerifiedEmail(boolean requireVerifiedEmail) {
            this.requireVerifiedEmail = requireVerifiedEmail;
        }
    }
}
