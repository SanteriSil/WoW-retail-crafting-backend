package com.crafting.service;

import com.crafting.model.CharacterProfession;
import com.crafting.model.CharacterRecipe;
import com.crafting.model.Recipe;
import com.crafting.model.WowCharacter;
import com.crafting.model.dto.DashboardResponse;
import com.crafting.model.dto.DashboardResponse.DashboardCraft;
import com.crafting.model.dto.ProfitEstimateDTO;
import com.crafting.repository.CharacterRecipeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CraftDashboardService {

    private final CharacterRecipeRepository characterRecipeRepository;
    private final ProfitCalculationService profitCalculationService;

    public CraftDashboardService(CharacterRecipeRepository characterRecipeRepository,
                                 ProfitCalculationService profitCalculationService) {
        this.characterRecipeRepository = characterRecipeRepository;
        this.profitCalculationService = profitCalculationService;
    }

    public DashboardResponse getDashboardCrafts(Long discordId, DashboardFilterParams params) {
        List<CharacterRecipe> allAssignments = characterRecipeRepository.findAllByDiscordIdWithDetails(discordId);

        // Deduplicate due to LEFT JOIN FETCH on ingredients producing duplicate rows
        List<CharacterRecipe> assignments = allAssignments.stream().distinct().toList();

        List<DashboardCraft> crafts = new ArrayList<>();

        for (CharacterRecipe cr : assignments) {
            WowCharacter character = cr.getCharacter();
            Recipe recipe = cr.getRecipe();

            // Apply filters
            if (params.characterId() != null && !character.getId().equals(params.characterId())) continue;
            if (params.professionId() != null && (recipe.getProfession() == null
                    || !recipe.getProfession().getId().equals(params.professionId()))) continue;
            if (params.search() != null && !params.search().isBlank()) {
                String q = params.search().toLowerCase();
                boolean matchesRecipe = recipe.getName().toLowerCase().contains(q);
                boolean matchesOutput = recipe.getOutputItem().getName().toLowerCase().contains(q);
                if (!matchesRecipe && !matchesOutput) continue;
            }

            // Find character's stats for this recipe's profession
            float multicraftPct = 0f;
            float resourcefulnessPct = 0f;
            if (recipe.getProfession() != null) {
                for (CharacterProfession cp : character.getProfessions()) {
                    if (cp.getProfession().getId().equals(recipe.getProfession().getId())) {
                        multicraftPct = cp.getMulticraftPercent() != null ? cp.getMulticraftPercent() : 0f;
                        resourcefulnessPct = cp.getResourcefulnessPercent() != null ? cp.getResourcefulnessPercent() : 0f;
                        break;
                    }
                }
            }

            ProfitEstimateDTO baseProfit = profitCalculationService.calculate(recipe);
            ProfitEstimateDTO adjustedProfit = profitCalculationService.calculate(recipe, multicraftPct, resourcefulnessPct);

            crafts.add(new DashboardCraft(
                    character.getId(),
                    character.getName(),
                    character.getIconUrl(),
                    recipe.getId(),
                    recipe.getName(),
                    recipe.getProfession() != null ? recipe.getProfession().getId() : null,
                    recipe.getProfession() != null ? recipe.getProfession().getName() : null,
                    recipe.getOutputItem().getId(),
                    recipe.getOutputItem().getName(),
                    recipe.getOutputQuantity(),
                    baseProfit,
                    adjustedProfit,
                    recipe.isMulticraftable(),
                    recipe.getMulticraftMultiplier(),
                    recipe.getResourcefulnessFactor(),
                    multicraftPct,
                    resourcefulnessPct,
                    adjustedProfit.missingPrices()
            ));
        }

        // Sort
        Comparator<DashboardCraft> comparator = sortComparator(params.sort());
        if ("desc".equalsIgnoreCase(params.direction())) {
            comparator = comparator.reversed();
        }
        crafts.sort(comparator);

        long totalBase = crafts.stream().mapToLong(c -> c.baseProfit().profit()).sum();
        long totalAdjusted = crafts.stream().mapToLong(c -> c.adjustedProfit().profit()).sum();

        return new DashboardResponse(crafts, totalBase, totalAdjusted, crafts.size());
    }

    private Comparator<DashboardCraft> sortComparator(String sort) {
        if (sort == null) sort = "profit";
        return switch (sort.toLowerCase()) {
            case "name" -> Comparator.comparing(DashboardCraft::recipeName, String.CASE_INSENSITIVE_ORDER);
            case "character" -> Comparator.comparing(DashboardCraft::characterName, String.CASE_INSENSITIVE_ORDER);
            case "profession" -> Comparator.comparing(
                    c -> c.professionName() != null ? c.professionName() : "",
                    String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparingLong(c -> c.adjustedProfit().profit());
        };
    }

    public record DashboardFilterParams(
            Long characterId,
            Integer professionId,
            String search,
            String sort,
            String direction
    ) {}
}
