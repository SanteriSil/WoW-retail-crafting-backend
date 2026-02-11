package com.crafting.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crafting.blizz.AHDataFetcher;

@RestController
@RequestMapping("/craftingAH")
public class AHFetchController {
    private final AHDataFetcher ahDataFetcher;

    public AHFetchController(AHDataFetcher ahDataFetcher) {
        this.ahDataFetcher = ahDataFetcher;
    }

    @GetMapping("/fetch")
    public ResponseEntity<String> fetchAHData() {
        try {
            boolean started = ahDataFetcher.triggerFetch();
            if (!started) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body("Fetch already running");
            }
            return ResponseEntity.accepted().body("Fetch started");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Fetch failed: " + e.getMessage());
        }
    }
}
