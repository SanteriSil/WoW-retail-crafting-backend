package com.crafting.controller;

import com.crafting.model.dto.CharacterDTO;
import com.crafting.model.dto.RecipeSummaryDTO;
import com.crafting.service.CharacterService;
import com.crafting.service.ConflictException;
import com.crafting.service.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/characters")
public class CharacterController {

    private static final Logger log = LoggerFactory.getLogger(CharacterController.class);

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping
    public ResponseEntity<List<CharacterDTO>> getMyCharacters(Authentication authentication) {
        Long discordId = parseDiscordId(authentication);
        log.debug("GET /characters for discordId={}", discordId);
        return ResponseEntity.ok(characterService.getMyCharacters(discordId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCharacter(@PathVariable Long id, Authentication authentication) {
        try {
            Long discordId = parseDiscordId(authentication);
            log.debug("GET /characters/{} for discordId={}", id, discordId);
            return ResponseEntity.ok(characterService.getCharacter(discordId, id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createCharacter(@RequestBody CharacterWriteRequest request,
                                             Authentication authentication) {
        try {
            Long discordId = parseDiscordId(authentication);
            log.debug("POST /characters for discordId={} name='{}' realm='{}' professionCount={}",
                    discordId,
                    request.name(),
                    request.realm(),
                    request.professions() != null ? request.professions().size() : 0);
            CharacterDTO created = characterService.createCharacter(discordId, toCommand(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCharacter(@PathVariable Long id,
                                             @RequestBody CharacterWriteRequest request,
                                             Authentication authentication) {
        try {
            Long discordId = parseDiscordId(authentication);
            log.debug("PUT /characters/{} for discordId={} name='{}' realm='{}' professionCount={}",
                    id,
                    discordId,
                    request.name(),
                    request.realm(),
                    request.professions() != null ? request.professions().size() : 0);
            CharacterDTO updated = characterService.updateCharacter(discordId, id, toCommand(request));
            return ResponseEntity.ok(updated);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCharacter(@PathVariable Long id, Authentication authentication) {
        try {
            Long discordId = parseDiscordId(authentication);
            log.debug("DELETE /characters/{} for discordId={}", id, discordId);
            characterService.deleteCharacter(discordId, id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/refresh-icon")
    public ResponseEntity<?> refreshIcon(@PathVariable Long id, Authentication authentication) {
        try {
            Long discordId = parseDiscordId(authentication);
            log.debug("POST /characters/{}/refresh-icon for discordId={}", id, discordId);
            CharacterDTO updated = characterService.refreshIcon(discordId, id);
            return ResponseEntity.ok(updated);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Recipe Assignment Endpoints ─────────────────────────────────────────

    @GetMapping("/{id}/recipes")
    public ResponseEntity<?> getAssignedRecipes(@PathVariable Long id, Authentication authentication) {
        try {
            Long discordId = parseDiscordId(authentication);
            log.debug("GET /characters/{}/recipes for discordId={}", id, discordId);
            List<RecipeSummaryDTO> recipes = characterService.getAssignedRecipes(discordId, id);
            return ResponseEntity.ok(recipes);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/recipes")
    public ResponseEntity<?> assignRecipes(@PathVariable Long id,
                                           @RequestBody AssignRecipesRequest request,
                                           Authentication authentication) {
        try {
            Long discordId = parseDiscordId(authentication);
            log.debug("POST /characters/{}/recipes for discordId={} recipeIds={}", id, discordId, request.recipeIds());
            characterService.assignRecipes(discordId, id, request.recipeIds());
            return ResponseEntity.ok(Map.of("assigned", request.recipeIds().size()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/recipes/{recipeId}")
    public ResponseEntity<?> unassignRecipe(@PathVariable Long id,
                                            @PathVariable Long recipeId,
                                            Authentication authentication) {
        try {
            Long discordId = parseDiscordId(authentication);
            log.debug("DELETE /characters/{}/recipes/{} for discordId={}", id, recipeId, discordId);
            characterService.unassignRecipe(discordId, id, recipeId);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private Long parseDiscordId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }

    private CharacterService.CreateCharacterCommand toCommand(CharacterWriteRequest request) {
        List<CharacterService.ProfessionCommand> professions = request.professions() == null
                ? List.of()
                : request.professions().stream()
                        .map(p -> new CharacterService.ProfessionCommand(
                                p.professionId(), p.multicraftPercent(), p.resourcefulnessPercent()))
                        .toList();
        return new CharacterService.CreateCharacterCommand(request.name(), request.realm(), professions);
    }

    // ── Request records ─────────────────────────────────────────────────────

    private record CharacterWriteRequest(
            String name,
            String realm,
            List<ProfessionRequest> professions
    ) {}

    private record ProfessionRequest(
            Integer professionId,
            Float multicraftPercent,
            Float resourcefulnessPercent
    ) {}

    private record AssignRecipesRequest(
            List<Long> recipeIds
    ) {}
}
