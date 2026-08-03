package com.example.demo.common;

/**
 * Shared page-size and offset clamping used by every paginated listing, so the deliberate
 * long-arithmetic overflow guard (see {@link #clampOffset}) exists in exactly one place.
 * Extracted from {@code ClubService} in commit fb79fba; see that history for why the two
 * clamps must be computed together (the offset must use the already-clamped limit).
 */
public final class PaginationClamps {

    private static final int MAX_PAGE_SIZE = 100;

    private PaginationClamps() {
    }

    public static int clampPageSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    // Mirrors the same "negative page treated as first page" rule clampOffset already applies
    // internally, so an envelope that echoes the page number back to the caller (see the club
    // post feed) reports what was actually served rather than a raw, possibly-negative input.
    public static int clampPage(int page) {
        return Math.max(0, page);
    }

    // Uses long arithmetic so a huge page number can't wrap around into a negative int offset,
    // and clamps to Integer.MAX_VALUE (the widest legal offset) rather than overflowing.
    public static int clampOffset(int page, int limit) {
        long offset = (long) Math.max(0, page) * limit;
        return (int) Math.min(offset, Integer.MAX_VALUE);
    }
}
