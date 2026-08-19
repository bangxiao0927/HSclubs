package com.example.demo.mobileauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Stores issued one-time codes as digests, and redeems them exactly once.
 *
 * <p>Only the SHA-256 of a code is kept, never the code itself: a read of this store must not be
 * enough to complete somebody's sign-in. Redemption is atomic -- the entry is removed as it is
 * read -- so two requests racing on the same code cannot both succeed, which is the replay
 * defence the contract requires.
 *
 * <p>In-memory on purpose for the template: codes live 60-120 seconds, so a restart simply forces
 * an in-flight sign-in to be retried, which is safe. A school that runs several instances behind a
 * load balancer would replace this with a shared store; the interface is the seam for that.
 */
@Component
public class MobileAuthCodeStore {

    /** An issued code: everything {@code complete} needs, and nothing that identifies the code. */
    public record Entry(
        String schoolId,
        String codeChallenge,
        Instant expiresAt,
        Authentication authentication,
        String returnTo
    ) {
    }

    private final Map<String, Entry> byHash = new ConcurrentHashMap<>();

    public void store(String code, Entry entry) {
        byHash.put(hash(code), entry);
    }

    /**
     * Removes and returns the entry for a code, or null if it was never issued or already spent.
     *
     * <p>Removal happens whether or not the entry has expired, so a late redemption cannot be
     * retried. The caller compares {@code expiresAt} against now to tell an expired code from a
     * valid one; either way the entry is gone after this call.
     */
    public Entry redeem(String code) {
        return byHash.remove(hash(code));
    }

    /** Drops expired entries; a school may call this on a schedule, but redemption is self-cleaning. */
    public void purgeExpired(Instant now) {
        byHash.values().removeIf(entry -> now.isAfter(entry.expiresAt()));
    }

    private static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec", e);
        }
    }
}
