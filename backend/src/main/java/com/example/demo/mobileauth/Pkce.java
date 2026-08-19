package com.example.demo.mobileauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * RFC 7636 PKCE, S256 only.
 *
 * <p>The app keeps a random verifier and sends only its SHA-256 challenge in the start request;
 * the verifier itself first appears at {@code complete}. Checking the two match here is what makes
 * a stolen one-time code useless on its own: whoever redeems the code must also hold the verifier
 * that produced the challenge the flow was started with.
 */
public final class Pkce {

    private static final Pattern CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Pattern VERIFIER = Pattern.compile("^[A-Za-z0-9._~-]{43,128}$");

    private Pkce() {
    }

    public static boolean isValidChallenge(String challenge) {
        return challenge != null && CHALLENGE.matcher(challenge).matches();
    }

    public static boolean isValidVerifier(String verifier) {
        return verifier != null && VERIFIER.matcher(verifier).matches();
    }

    /** Whether {@code verifier} hashes to {@code challenge} under S256. Constant-time on the digest. */
    public static boolean matches(String verifier, String challenge) {
        if (!isValidVerifier(verifier) || !isValidChallenge(challenge)) {
            return false;
        }
        String computed = challengeFor(verifier);
        return MessageDigest.isEqual(
            computed.getBytes(StandardCharsets.US_ASCII),
            challenge.getBytes(StandardCharsets.US_ASCII));
    }

    /** The S256 challenge for a verifier: base64url(SHA-256(verifier)), no padding. */
    public static String challengeFor(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
        }
    }
}
