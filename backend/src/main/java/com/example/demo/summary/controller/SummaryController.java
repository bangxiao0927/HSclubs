package com.example.demo.summary.controller;

import com.example.demo.summary.model.SummaryResponse;
import com.example.demo.summary.service.SummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

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
     * carries an entity tag covering the whole representation, and a poll that sends the tag it
     * already has back as {@code If-None-Match} gets a 304 with no body.
     *
     * <p>That is the same question {@code dataHash} was introduced to answer ("has anything
     * changed?"), asked the way HTTP already asks it -- so a caller gets it for free from any
     * HTTP client, without having to parse the body first. The tag is deliberately not
     * {@code dataHash} itself; see {@link SummaryService#buildSnapshot()}. Spring Security sets
     * {@code Cache-Control: no-store} on every response, so nothing is cached behind our back:
     * the client still asks each time, it just does not have to download an unchanged directory.
     */
    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> getSummary(WebRequest request) {
        SummaryService.Snapshot snapshot = summaryService.buildSnapshot();
        String etag = "\"" + snapshot.entityTag() + "\"";

        // Spring's own conditional-request handling: it parses multi-valued and weakened
        // If-None-Match headers per RFC 9110 and sets the ETag response header itself, so this
        // controller has no header parsing of its own to get wrong.
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok().eTag(etag).body(snapshot.summary());
    }
}
