package com.crafting.controller;

import com.crafting.scraper.WowheadScraper;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scraper")
public class ScraperController {

    private final WowheadScraper wowheadScraper;

    public ScraperController(WowheadScraper wowheadScraper) {
        this.wowheadScraper = wowheadScraper;
    }

    @PostMapping("/trigger")
    public ResponseEntity<?> trigger(@RequestBody ScraperTriggerRequest request) {
        try {
            boolean queued;
            if (request.professionId() != null && request.expansionId() != null) {
                queued = wowheadScraper.triggerScrape(request.professionId(), request.expansionId());
            } else if (request.professionSlug() != null && request.expansionSlug() != null) {
                queued = wowheadScraper.triggerScrape(request.professionSlug(), request.expansionSlug());
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Provide either professionId+expansionId or professionSlug+expansionSlug"));
            }

            if (!queued) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Scraper is already running"));
            }

            return ResponseEntity.accepted().body(Map.of("status", "queued"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<WowheadScraper.ScraperStatus> status() {
        return ResponseEntity.ok(wowheadScraper.getStatus());
    }

    private record ScraperTriggerRequest(
            Integer professionId,
            Integer expansionId,
            String professionSlug,
            String expansionSlug
    ) {
    }
}
