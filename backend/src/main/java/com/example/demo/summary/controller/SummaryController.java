package com.example.demo.summary.controller;

import com.example.demo.summary.model.SummaryResponse;
import com.example.demo.summary.service.SummaryService;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/summary")
    public SummaryResponse getSummary() {
        return summaryService.buildSummary();
    }
}
