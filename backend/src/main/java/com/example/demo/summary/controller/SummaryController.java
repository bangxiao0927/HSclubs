package com.example.demo.summary.controller;

import com.example.demo.summary.model.SummaryResponse;
import com.example.demo.summary.service.SummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public summary endpoint consumed by the 2nd-repo aggregator.
 * Returns club directory stats and a data hash for change detection.
 */
@RestController
@RequestMapping("/api")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /**
     * The aggregator polls this endpoint, so it also answers conditional requests: the response
     * carries the {@code dataHash} as its ETag, and a poll that sends the hash it already has
     * back as {@code If-None-Match} gets a 304 with no body.
     *
     * <p>That is the same question {@code dataHash} was introduced to answer ("has anything
     * changed?"), asked the way HTTP already asks it -- so a caller gets it for free from any
     * HTTP client, without having to parse the body first. Spring Security sets
     * {@code Cache-Control: no-store} on every response, so nothing is cached behind our back:
     * the client still asks each time, it just does not have to download an unchanged directory.
     */
    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> getSummary(
        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        SummaryResponse summary = summaryService.buildSummary();
        String etag = "\"" + summary.getDataHash() + "\"";

        if (matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(summary);
    }

    /**
     * A conservative If-None-Match check: the header may carry several tags, and a caller (or a
     * proxy) may weaken ours to {@code W/"..."}. Anything unparsed simply misses and gets the
     * full body, which is always correct.
     */
    private static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String trimmed = candidate.trim();
            if ("*".equals(trimmed)) {
                return true;
            }
            if (trimmed.startsWith("W/")) {
                trimmed = trimmed.substring(2);
            }
            if (trimmed.equals(etag)) {
                return true;
            }
        }
        return false;
    }
}
