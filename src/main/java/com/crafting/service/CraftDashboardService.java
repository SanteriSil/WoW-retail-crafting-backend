package com.crafting.service;

import com.crafting.model.CharacterProfession;
import com.crafting.model.CharacterRecipe;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeCharacterStatOverride;
import com.crafting.model.WowCharacter;
import com.crafting.model.dto.DashboardResponse;
import com.crafting.model.dto.DashboardResponse.DashboardCraft;
import com.crafting.model.dto.ProfitEstimateDTO;
import com.crafting.repository.CharacterRecipeRepository;
import com.crafting.repository.RecipeCharacterStatOverrideRepository;
import com.crafting.repository.RecipeRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CraftDashboardService {

    private static final Logger log = LoggerFactory.getLogger(CraftDashboardService.class);

    private final CharacterRecipeRepository characterRecipeRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeCharacterStatOverrideRepository statOverrideRepository;
    private final ProfitCalculationService profitCalculationService;

    public CraftDashboardService(CharacterRecipeRepository characterRecipeRepository,
                                 RecipeRepository recipeRepository,
                                 RecipeCharacterStatOverrideRepository statOverrideRepository,
                                 ProfitCalculationService profitCalculationService) {
        this.characterRecipeRepository = characterRecipeRepository;
        this.recipeRepository = recipeRepository;
        this.statOverrideRepository = statOverrideRepository;
        this.profitCalculationService = profitCalculationService;
    }

    @Transactional
    public DashboardResponse getDashboardCrafts(Long discordId, DashboardFilterParams params) {
        log.debug("Loading dashboard crafts for discordId={} characterId={} professionId={} search='{}' sort={} direction={}",
                discordId,
                params.characterId(),
                params.professionId(),
                params.search(),
                params.sort(),
                params.direction());
        List<CharacterRecipe> allAssignments = characterRecipeRepository.findAllByDiscordIdWithDetails(discordId);
        log.debug("Fetched {} raw character recipe assignments for discordId={}", allAssignments.size(), discordId);

        // Prime the persistence context with correctly-loaded ingredient collections.
        // Without this, the JOIN FETCH on ingredients in the CharacterRecipe query would
        // produce a Cartesian product: N CharacterRecipes × M ingredients per recipe,
        // inflating each recipe's ingredient list by the number of characters sharing it.
        Set<Long> recipeIds = allAssignments.stream()
                .map(cr -> cr.getRecipe().getId())
                .collect(Collectors.toSet());
        if (!recipeIds.isEmpty()) {
            recipeRepository.findByIdsWithIngredients(recipeIds);
            log.debug("Primed ingredient cache for {} distinct recipes", recipeIds.size());
        }

        // Deduplicate due to LEFT JOIN FETCH on ingredients producing duplicate rows
        List<CharacterRecipe> assignments = allAssignments.stream().distinct().toList();
        if (allAssignments.size() != assignments.size()) {
            log.debug("Deduplicated dashboard assignments for discordId={} from {} to {}", discordId, allAssignments.size(), assignments.size());
        }

        List<DashboardCraft> crafts = new ArrayList<>();
        Set<Long> characterIds = assignments.stream().map(cr -> cr.getCharacter().getId()).collect(Collectors.toSet());
        List<RecipeCharacterStatOverride> overrides = recipeIds.isEmpty() || characterIds.isEmpty()
            ? List.of()
            : statOverrideRepository.findByRecipe_IdInAndCharacter_IdIn(recipeIds, characterIds);
        var overrideByPair = overrides.stream()
            .collect(Collectors.toMap(
                o -> overrideKey(o.getRecipe().getId(), o.getCharacter().getId()),
                o -> o,
                (left, right) -> right
            ));

        for (CharacterRecipe cr : assignments) {
            WowCharacter character = cr.getCharacter();
            Recipe recipe = cr.getRecipe();

            // Apply filters
            if (params.characterId() != null && !character.getId().equals(params.characterId())) {
                log.debug("Skipping dashboard craft characterId={} recipeId={} due to character filter {}",
                        character.getId(), recipe.getId(), params.characterId());
                continue;
            }
            if (params.professionId() != null && (recipe.getProfession() == null
                    || !recipe.getProfession().getId().equals(params.professionId()))) {
                log.debug("Skipping dashboard craft characterId={} recipeId={} due to profession filter {} actualProfessionId={}",
                        character.getId(),
                        recipe.getId(),
                        params.professionId(),
                        recipe.getProfession() != null ? recipe.getProfession().getId() : null);
                continue;
            }
            if (params.search() != null && !params.search().isBlank()) {
                String q = params.search().toLowerCase();
                boolean matchesRecipe = recipe.getName().toLowerCase().contains(q);
                boolean matchesOutput = recipe.getOutputItem().getName().toLowerCase().contains(q);
                if (!matchesRecipe && !matchesOutput) {
                    log.debug("Skipping dashboard craft characterId={} recipeId={} due to search filter '{}'",
                            character.getId(), recipe.getId(), params.search());
                    continue;
                }
            }

            // Find character's stats for this recipe's profession
            float baseMulticraftPct = 0f;
            float baseResourcefulnessPct = 0f;
            if (recipe.getProfession() != null) {
                for (CharacterProfession cp : character.getProfessions()) {
                    if (cp.getProfession().getId().equals(recipe.getProfession().getId())) {
                        baseMulticraftPct = cp.getMulticraftPercent() != null ? cp.getMulticraftPercent() : 0f;
                        baseResourcefulnessPct = cp.getResourcefulnessPercent() != null ? cp.getResourcefulnessPercent() : 0f;
                        break;
                    }
                }
            }

            RecipeCharacterStatOverride statOverride = overrideByPair.get(overrideKey(recipe.getId(), character.getId()));
            boolean statOverrideActive = statOverride != null;
            float multicraftPct = statOverrideActive ? statOverride.getMulticraftPercent() : baseMulticraftPct;
            float resourcefulnessPct = statOverrideActive ? statOverride.getResourcefulnessPercent() : baseResourcefulnessPct;

            ProfitEstimateDTO baseProfit = profitCalculationService.calculate(recipe);
            ProfitEstimateDTO adjustedProfit = profitCalculationService.calculate(recipe, multicraftPct, resourcefulnessPct);
            ProfitCalculationService.CostBreakdown costBreakdown = profitCalculationService.calculateCostBreakdown(recipe);

        log.debug("Dashboard craft characterId={} recipeId={} professionId={} ingredients={} optionalGroups={} baseCost={} adjustedCost={} baseProfit={} adjustedProfit={} multicraftPercent={} resourcefulnessPercent={} missingPrices={}",
            character.getId(),
            recipe.getId(),
            recipe.getProfession() != null ? recipe.getProfession().getId() : null,
            recipe.getIngredients() != null ? recipe.getIngredients().size() : 0,
            recipe.getOptionalIngredientGroups() != null ? recipe.getOptionalIngredientGroups().size() : 0,
            costBreakdown.baseMaterialsCost() + costBreakdown.optionalReagentsCost(),
            adjustedProfit.ingredientCost(),
            baseProfit.profit(),
            adjustedProfit.profit(),
            multicraftPct,
            resourcefulnessPct,
            adjustedProfit.missingPrices());

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
                    ProfitCalculationService.resolvePrice(recipe.getOutputItem()),
                    recipe.getOutputQuantity(),
                    recipe.getOutputItem().getQuality(),
                    baseProfit,
                    adjustedProfit,
                    costBreakdown.baseMaterialsCost(),
                    costBreakdown.optionalReagentsCost(),
                    recipe.isMulticraftable(),
                    recipe.getMulticraftMultiplier(),
                    recipe.getResourcefulnessFactor(),
                    baseMulticraftPct,
                    baseResourcefulnessPct,
                    multicraftPct,
                    resourcefulnessPct,
                    statOverrideActive,
                    adjustedProfit.missingPrices(),
                    recipe.getNotes() != null && !recipe.getNotes().isBlank(),
                    recipe.getNotes()
            ));
        }

        // Sort
        Comparator<DashboardCraft> comparator = sortComparator(params.sort());
        if ("desc".equalsIgnoreCase(params.direction())) {
            comparator = comparator.reversed();
        }
        crafts.sort(comparator);

        long totalBase = crafts.stream().mapToLong(c -> c.baseProfit().profit()).sum();
        long totalBaseCost = crafts.stream().mapToLong(c -> c.baseProfit().ingredientCost()).sum();
        long totalAdjusted = crafts.stream().mapToLong(c -> c.adjustedProfit().profit()).sum();

        log.debug("Returning {} dashboard crafts for discordId={} totalBaseCost={} totalBaseProfit={} totalAdjustedProfit={}",
            crafts.size(), discordId, totalBaseCost, totalBase, totalAdjusted);

        return new DashboardResponse(crafts, totalBaseCost, totalBase, totalAdjusted, crafts.size());
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

    private String overrideKey(Long recipeId, Long characterId) {
        return recipeId + ":" + characterId;
    }
}
