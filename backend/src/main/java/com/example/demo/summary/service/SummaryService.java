package com.example.demo.summary.service;

import com.example.demo.club.mapper.ClubMapper;
import com.example.demo.club.model.Club;
import com.example.demo.summary.model.SummaryResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SummaryService {

    private final ClubMapper clubMapper;

    public SummaryService(ClubMapper clubMapper) {
        this.clubMapper = clubMapper;
    }

    public SummaryResponse buildSummary() {
        List<Club> clubs = clubMapper.findAll();

        Map<String, Integer> categoryCounts = clubs.stream()
            .filter(c -> c.getCategory() != null && !c.getCategory().isBlank())
            .collect(Collectors.groupingBy(
                Club::getCategory,
                LinkedHashMap::new,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));

        int totalMembers = clubs.stream()
            .mapToInt(c -> c.getMemberCount() != null ? c.getMemberCount() : 0)
            .sum();

        LocalDateTime lastUpdated = clubs.stream()
            .map(Club::getUpdatedAt)
            .filter(t -> t != null)
            .max(LocalDateTime::compareTo)
            .orElse(null);

        SummaryResponse summary = new SummaryResponse();
        summary.setSchoolName("HS Clubs");
        summary.setShortName("HS Clubs");
        summary.setSlug("hsclubs");
        summary.setStatus("active");
        summary.setClubCount(clubs.size());
        summary.setCategories(categoryCounts);
        summary.setMemberCount(totalMembers);
        summary.setLastUpdatedAt(lastUpdated);
        summary.setDataHash(computeHash(clubs));

        return summary;
    }

    /**
     * Compute a deterministic SHA-256 hash from club identities.
     * The 2nd repo can compare this hash to detect changes without
     * re-fetching all club data.
     */
    private String computeHash(List<Club> clubs) {
        String payload = clubs.stream()
            .sorted((a, b) -> Long.compare(a.getId() != null ? a.getId() : 0,
                                           b.getId() != null ? b.getId() : 0))
            .map(c -> (c.getId() != null ? c.getId() : "") + "|"
                    + (c.getName() != null ? c.getName() : "") + "|"
                    + (c.getCategory() != null ? c.getCategory() : "") + "|"
                    + (c.getMemberCount() != null ? c.getMemberCount() : 0))
            .collect(Collectors.joining("\n"));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
