package com.example.demo.club.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ClubLocationNormalizer {

    private static final Pattern ROOM_PREFIX = Pattern.compile("^room\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOM_NUMBER = Pattern.compile("^(?:[A-Za-z]\\d+|\\d+)(?:\\b.*)?$");

    private ClubLocationNormalizer() {
    }

    static String normalize(String location) {
        if (location == null) {
            return null;
        }

        String trimmed = location.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        Matcher prefixedRoom = ROOM_PREFIX.matcher(trimmed);
        if (prefixedRoom.matches()) {
            return "Room " + prefixedRoom.group(1).trim();
        }
        if (ROOM_NUMBER.matcher(trimmed).matches()) {
            return "Room " + trimmed;
        }
        return trimmed;
    }
}
