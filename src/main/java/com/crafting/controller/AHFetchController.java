package com.crafting.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.crafting.blizz.AHDataFetcher;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/craftingAH")
public class AHFetchController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AHFetchController.class);
    private final AHDataFetcher ahDataFetcher;

    public AHFetchController(AHDataFetcher ahDataFetcher) {
        this.ahDataFetcher = ahDataFetcher;
    }

    @GetMapping("/fetch")
    public ResponseEntity<String> fetchAHData() {
        logger.info("Received request to refresh AH data");
        try {
            boolean started = ahDataFetcher.triggerFetch();
            if (!started) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body("Fetch already running");
            }
            logger.info("AH data fetch triggered successfully");
            return ResponseEntity.accepted().body("Fetch started");
        } catch (Exception e) {
            logger.error("Error triggering AH data fetch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Fetch failed: " + e.getMessage());
        }
    }
}
