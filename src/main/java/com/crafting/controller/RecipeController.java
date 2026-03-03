package com.crafting.controller;

import com.crafting.model.dto.ProfitEstimateDTO;
import com.crafting.model.dto.RecipeDTO;
import com.crafting.model.dto.RecipeSummaryDTO;
import com.crafting.service.ConflictException;
import com.crafting.service.RecipeService;
import com.crafting.service.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/spell-ids")
    public ResponseEntity<List<Long>> getSpellIds(@RequestParam(required = false) Integer expansionId) {
        return ResponseEntity.ok(recipeService.getSpellIds(expansionId));
    }

    @GetMapping
    public ResponseEntity<Page<RecipeSummaryDTO>> getRecipes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort,
            @RequestParam(required = false) Integer professionId,
            @RequestParam(required = false) Integer expansionId,
            @RequestParam(required = false) Long outputItemId,
            @RequestParam(required = false) Long ingredientItemId,
            @RequestParam(required = false) String search
    ) {
        int cappedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), cappedSize, parseSort(sort));
        return ResponseEntity.ok(recipeService.getRecipes(
                professionId,
                expansionId,
                outputItemId,
                ingredientItemId,
                search,
                pageable
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRecipe(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(recipeService.getRecipe(id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/profit")
    public ResponseEntity<?> getRecipeProfit(@PathVariable Long id) {
        try {
            ProfitEstimateDTO profit = recipeService.getRecipeProfit(id);
            return ResponseEntity.ok(profit);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createRecipe(@RequestBody RecipeWriteRequest request, Authentication authentication) {
        try {
            Long callerDiscordId = parseCallerDiscordId(authentication);
            RecipeDTO created = recipeService.createRecipe(toCommand(request), callerDiscordId);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRecipe(@PathVariable Long id, @RequestBody RecipeWriteRequest request) {
        try {
            RecipeDTO updated = recipeService.updateRecipe(id, toCommand(request));
            return ResponseEntity.ok(updated);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<?> duplicateRecipe(@PathVariable Long id, Authentication authentication) {
        try {
            Long callerDiscordId = parseCallerDiscordId(authentication);
            RecipeDTO duplicated = recipeService.duplicateRecipe(id, callerDiscordId);
            return ResponseEntity.status(HttpStatus.CREATED).body(duplicated);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRecipe(@PathVariable Long id) {
        try {
            recipeService.softDeleteRecipe(id);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private Sort parseSort(String rawSort) {
        String[] parts = rawSort == null ? new String[0] : rawSort.split(",");
        String property = parts.length > 0 && !parts[0].isBlank() ? parts[0] : "name";
        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1]))
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    private RecipeService.CreateOrUpdateRecipeCommand toCommand(RecipeWriteRequest request) {
        List<RecipeService.IngredientCommand> ingredients = request.ingredients() == null
                ? List.of()
                : request.ingredients().stream()
                        .map(i -> new RecipeService.IngredientCommand(i.itemId(), i.quantity()))
                        .toList();

        List<RecipeService.OptionalIngredientGroupCommand> optionalGroups = request.optionalIngredientGroups() == null
                ? List.of()
                : request.optionalIngredientGroups().stream()
                        .map(g -> new RecipeService.OptionalIngredientGroupCommand(
                                g.slotIndex(),
                                g.label(),
                                g.options() == null ? List.of() : g.options().stream()
                                        .map(o -> new RecipeService.OptionalIngredientOptionCommand(o.itemId(), o.quantity()))
                                        .toList()
                        ))
                        .toList();

        return new RecipeService.CreateOrUpdateRecipeCommand(
                request.name(),
                request.wowheadSpellId(),
                request.outputItemId(),
                request.outputQuantity(),
                request.professionId(),
                request.expansionId(),
                request.source(),
                ingredients,
                optionalGroups,
                request.multicraftable(),
                request.multicraftMultiplier(),
                request.resourcefulnessFactor()
        );
    }

    private Long parseCallerDiscordId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record RecipeWriteRequest(
            String name,
            Long wowheadSpellId,
            Long outputItemId,
            Float outputQuantity,
            Integer professionId,
            Integer expansionId,
            String source,
            List<IngredientRequest> ingredients,
            List<OptionalIngredientGroupRequest> optionalIngredientGroups,
            Boolean multicraftable,
            Float multicraftMultiplier,
            Float resourcefulnessFactor
    ) {
    }

    private record IngredientRequest(Long itemId, Integer quantity) {
    }

    private record OptionalIngredientGroupRequest(
            Integer slotIndex,
            String label,
            List<OptionalIngredientOptionRequest> options
    ) {
    }

    private record OptionalIngredientOptionRequest(Long itemId, Integer quantity) {
    }
}
