package com.example.demo.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import com.example.demo.auth.config.SecurityProperties;

class LoginEligibilityPolicyTest {

    private static LoginEligibilityPolicy policy(List<String> allowedDomains, boolean requireVerifiedEmail) {
        SecurityProperties properties = new SecurityProperties();
        properties.getLogin().setAllowedEmailDomains(allowedDomains);
        properties.getLogin().setRequireVerifiedEmail(requireVerifiedEmail);
        return new LoginEligibilityPolicy(properties);
    }

    private static Map<String, Object> account(String email, Object emailVerified) {
        return Map.of("email", email, "email_verified", emailVerified);
    }

    // The default has to stay open: other schools copy this repo, and a fresh deployment that
    // silently rejected every account would be a terrible first run.
    @Test
    void allowsAnyAccountWhenNothingIsConfigured() {
        assertThatCode(() -> policy(List.of(), false).verifyEligible(account("anyone@gmail.com", false)))
            .doesNotThrowAnyException();
    }

    @Test
    void allowsAnAccountFromAConfiguredDomain() {
        assertThatCode(() -> policy(List.of("students.example.edu"), false)
            .verifyEligible(account("ada@students.example.edu", true)))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnAccountFromAnotherDomain() {
        assertThatThrownBy(() -> policy(List.of("students.example.edu"), false)
            .verifyEligible(account("stranger@gmail.com", true)))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .hasMessageContaining("email domain");
    }

    @Test
    void domainMatchingIgnoresCaseAndAStrayAtSignOrWhitespaceInConfiguration() {
        LoginEligibilityPolicy policy = policy(List.of(" @Students.Example.EDU "), false);

        assertThatCode(() -> policy.verifyEligible(account("ADA@students.example.edu", true)))
            .doesNotThrowAnyException();
    }

    // Only the domain after the last @ counts; a local part that embeds another domain must not
    // be able to impersonate one.
    @Test
    void rejectsAnAddressThatOnlyLooksLikeTheAllowedDomain() {
        LoginEligibilityPolicy policy = policy(List.of("students.example.edu"), false);

        assertThatThrownBy(() -> policy.verifyEligible(account("ada@students.example.edu.evil.com", true)))
            .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> policy.verifyEligible(account("ada@students.example.edu@gmail.com", true)))
            .isInstanceOf(OAuth2AuthenticationException.class);
    }

    // Every downstream authorization decision -- owner-email comparison, oauth_users lookup --
    // is keyed on the email address, so an account without one cannot satisfy a domain rule.
    @Test
    void rejectsAnAccountWithNoEmailWhenADomainIsRequired() {
        assertThatThrownBy(() -> policy(List.of("students.example.edu"), false).verifyEligible(Map.of()))
            .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void requiresAVerifiedEmailOnlyWhenConfiguredTo() {
        assertThatCode(() -> policy(List.of(), false).verifyEligible(account("ada@gmail.com", false)))
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> policy(List.of(), true).verifyEligible(account("ada@gmail.com", false)))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .hasMessageContaining("not verified");
    }

    @Test
    void acceptsAVerifiedFlagSentAsAString() {
        assertThatCode(() -> policy(List.of(), true).verifyEligible(account("ada@gmail.com", "true")))
            .doesNotThrowAnyException();
    }

    @Test
    void treatsAMissingVerifiedClaimAsNotVerifiedWhenTheCheckIsOn() {
        assertThatThrownBy(() -> policy(List.of(), true).verifyEligible(Map.of("email", "ada@gmail.com")))
            .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
