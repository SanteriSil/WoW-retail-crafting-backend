package com.crafting.controller;

import com.crafting.auth.ActorContextService;
import com.crafting.service.RecipeService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scraper")
public class ScraperController {

    private static final String WOWHEAD_PREFIX = "https://www.wowhead.com/";
    private static final String USER_AGENT = "WoWCraftingBot/1.0";

    private final RecipeService recipeService;
    private final ActorContextService actorContextService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ScraperController(RecipeService recipeService, ActorContextService actorContextService) {
        this.recipeService = recipeService;
        this.actorContextService = actorContextService;
    }

    @GetMapping(value = "/proxy", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> proxy(@RequestParam String url) {
        if (url == null || !url.startsWith(WOWHEAD_PREFIX)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "URL must start with " + WOWHEAD_PREFIX));
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("error", "Wowhead returned HTTP " + response.statusCode()));
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                    .body(response.body());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Failed to fetch URL: " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Request interrupted"));
        }
    }

    @PostMapping("/import")
    public ResponseEntity<?> importRecipes(@RequestBody List<RecipeImportCommand> commands,
                                           Authentication authentication) {
        try {
            var actorSnapshot = actorContextService.extractActorSnapshot(authentication);
            if (actorSnapshot.discordId() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authenticated actor is required"));
            }
            RecipeService.ImportResult result = recipeService.importRecipes(commands, actorSnapshot);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public record RecipeImportCommand(
            Long wowheadSpellId,
            String recipeName,
            Long outputItemId,
            Float outputQuantity,
            Integer professionId,
            Integer expansionId,
            List<IngredientImport> ingredients
    ) {}

    public record IngredientImport(
            Long itemId,
            Integer quantity
    ) {}
}
