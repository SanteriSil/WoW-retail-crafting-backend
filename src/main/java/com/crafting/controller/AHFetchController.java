package com.crafting.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crafting.blizz.AHDataFetcher;

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
        logger.debug("Manual fetch trigger called");
        try {
            boolean started = ahDataFetcher.triggerFetch();
            if (!started) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body("Fetch already running");
            }
            logger.debug("AH data fetch triggered successfully");
            return ResponseEntity.accepted().body("Fetch started");
        } catch (Exception e) {
            logger.error("Error triggering AH data fetch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Fetch failed: " + e.getMessage());
        }
    }

    /**
     * Accepts user-submitted CSV auction data from an in-game addon.
     * Each line: itemId,unitPrice,quantity
     */
    @PostMapping("/submit")
    public ResponseEntity<String> submitAuctionData(@RequestBody String csvBody) {
        logger.debug("User auction data submission received ({} chars)", csvBody.length());
        try {
            int updated = ahDataFetcher.submitAuctionData(csvBody);
            if (updated < 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body("Another fetch is already running");
            }
            logger.info("User auction submission processed – {} items updated", updated);
            return ResponseEntity.ok(updated + " item prices updated");
        } catch (Exception e) {
            logger.error("Error processing user-submitted auction data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Submit failed: " + e.getMessage());
        }
    }
}
