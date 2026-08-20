package com.example.demo.summary.controller;

import com.example.demo.summary.model.SummaryResponse;
import com.example.demo.summary.config.SchoolIdentity;
import com.example.demo.summary.model.SummaryV1Response;
import com.example.demo.summary.service.SummaryService;
import com.example.demo.summary.service.SummaryUsage;
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
    private final SchoolIdentity schoolIdentity;
    private final SummaryUsage usage;

    public SummaryController(SummaryService summaryService, SchoolIdentity schoolIdentity, SummaryUsage usage) {
        this.summaryService = summaryService;
        this.schoolIdentity = schoolIdentity;
        this.usage = usage;
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
        // Legacy observation: count the read before anything else, whether or not it ends in a 304.
        usage.record(SummaryUsage.LEGACY);
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

    /**
     * The same directory, with the identity and version marker the v1 contract requires.
     *
     * <p>Built from the same snapshot as the unversioned endpoint, so the two can never report
     * different numbers, and answering conditional requests the same way -- with a distinct
     * entity tag, because the two representations differ and a client must not be told its copy
     * of one is current when it holds the other.
     *
     * <p>404 until this deployment has been given an identity. That is not an error state: a
     * school joins v1 by configuring the value its registry issued, and until then the only
     * honest answer to "what is your v1 summary" is that there is not one.
     */
    @GetMapping("/v1/summary")
    public ResponseEntity<SummaryV1Response> getSummaryV1(WebRequest request) {
        String schoolId = schoolIdentity.schoolId().orElse(null);
        if (schoolId == null) {
            return ResponseEntity.notFound().build();
        }
        usage.record(SummaryUsage.V1);

        SummaryService.Snapshot snapshot = summaryService.buildSnapshot();
        String etag = "\"v1-" + snapshot.entityTag() + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok().eTag(etag).body(new SummaryV1Response(schoolId, snapshot.summary()));
    }
}
