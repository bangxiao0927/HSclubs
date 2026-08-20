package com.example.demo.mobileauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PkceTest {

    // The pinned pair from contracts/v1/vectors/mobile-auth.json, which all three repositories
    // assert against.
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void derivesThePinnedChallengeFromThePinnedVerifier() {
        assertThat(Pkce.challengeFor(VERIFIER)).isEqualTo(CHALLENGE);
        assertThat(Pkce.matches(VERIFIER, CHALLENGE)).isTrue();
    }

    @Test
    void rejectsAVerifierThatDoesNotHashToTheChallenge() {
        assertThat(Pkce.matches("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", CHALLENGE)).isFalse();
    }

    @Test
    void refusesMalformedVerifiersAndChallenges() {
        assertThat(Pkce.isValidVerifier("tooshort")).isFalse();
        assertThat(Pkce.isValidChallenge("not-43-characters")).isFalse();
        assertThat(Pkce.matches("tooshort", CHALLENGE)).isFalse();
    }
}
