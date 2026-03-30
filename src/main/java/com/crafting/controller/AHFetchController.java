package com.crafting.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crafting.blizz.AHDataFetcher;
import com.crafting.service.RecipeService;

@RestController
@RequestMapping("/craftingAH")
public class AHFetchController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AHFetchController.class);
    private final AHDataFetcher ahDataFetcher;
    private final RecipeService recipeService;

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? String.valueOf(auth.getPrincipal()) : "anonymous";
    }

    public AHFetchController(AHDataFetcher ahDataFetcher, RecipeService recipeService) {
        this.ahDataFetcher = ahDataFetcher;
        this.recipeService = recipeService;
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
            logger.info("[{}] Auction submission processed – {} items updated", currentUser(), updated);
            return ResponseEntity.ok(updated + " item prices updated");
        } catch (Exception e) {
            logger.error("Error processing user-submitted auction data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Submit failed: " + e.getMessage());
        }
    }

    @PostMapping("/fetch-for-recipes")
    public ResponseEntity<?> fetchForRecipes(@RequestBody FetchForRecipesRequest request) {
        List<Long> recipeIds = request.recipeIds() == null ? List.of() : request.recipeIds().stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (recipeIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "recipeIds must contain at least one valid ID"));
        }

        try {
            Set<Integer> itemIds = recipeService.getTrackedItemIdsForRecipes(recipeIds);
            if (itemIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No tracked items found for the selected recipes"));
            }

            int updatedCount = ahDataFetcher.triggerFetchForItems(itemIds);
            if (updatedCount < 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Fetch already running"));
            }

            return ResponseEntity.ok(Map.of(
                    "updatedCount", updatedCount,
                "itemIds", itemIds,
                    "recipeIds", recipeIds
            ));
        } catch (Exception e) {
            logger.error("Error triggering targeted AH refresh for recipes {}", recipeIds, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Fetch failed: " + e.getMessage()));
        }
    }

    private record FetchForRecipesRequest(List<Long> recipeIds) {
    }
}
