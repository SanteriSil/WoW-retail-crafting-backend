package com.crafting.controller;

import com.crafting.model.dto.DashboardResponse;
import com.crafting.model.dto.RecipeCharacterStatOverrideDTO;
import com.crafting.service.CraftDashboardService;
import com.crafting.service.RecipeCharacterStatOverrideService;
import com.crafting.service.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final CraftDashboardService craftDashboardService;
    private final RecipeCharacterStatOverrideService statOverrideService;

    public DashboardController(
            CraftDashboardService craftDashboardService,
            RecipeCharacterStatOverrideService statOverrideService
    ) {
        this.craftDashboardService = craftDashboardService;
        this.statOverrideService = statOverrideService;
    }

    @GetMapping("/crafts")
    public ResponseEntity<DashboardResponse> getDashboardCrafts(
            @RequestParam(required = false) Long characterId,
            @RequestParam(required = false) Integer professionId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "profit") String sort,
            @RequestParam(required = false, defaultValue = "desc") String direction,
            Authentication authentication) {

        Long discordId = Long.parseLong(authentication.getName());
    log.debug("GET /dashboard/crafts for discordId={} characterId={} professionId={} search='{}' sort={} direction={}",
        discordId,
        characterId,
        professionId,
        search,
        sort,
        direction);
        var params = new CraftDashboardService.DashboardFilterParams(
                characterId, professionId, search, sort, direction);
        return ResponseEntity.ok(craftDashboardService.getDashboardCrafts(discordId, params));
    }

    @GetMapping("/stat-overrides")
    public ResponseEntity<List<RecipeCharacterStatOverrideDTO>> listStatOverrides(Authentication authentication) {
        Long discordId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(statOverrideService.listForDiscordUser(discordId));
    }

    @PutMapping("/stat-overrides")
    public ResponseEntity<?> upsertStatOverride(
            @RequestBody DashboardStatOverrideWriteRequest request,
            Authentication authentication
    ) {
        Long discordId = Long.parseLong(authentication.getName());
        try {
            RecipeCharacterStatOverrideDTO dto = statOverrideService.upsert(
                    discordId,
                    request.recipeId(),
                    request.characterId(),
                    request.multicraftPercent(),
                    request.resourcefulnessPercent()
            );
            return ResponseEntity.ok(dto);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/stat-overrides")
    public ResponseEntity<?> deleteStatOverride(
            @RequestParam Long recipeId,
            @RequestParam Long characterId,
            Authentication authentication
    ) {
        Long discordId = Long.parseLong(authentication.getName());
        try {
            statOverrideService.delete(discordId, recipeId, characterId);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private record DashboardStatOverrideWriteRequest(
            Long recipeId,
            Long characterId,
            Float multicraftPercent,
            Float resourcefulnessPercent
    ) {
    }
}
