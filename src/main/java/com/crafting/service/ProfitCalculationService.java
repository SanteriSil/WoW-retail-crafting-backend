package com.crafting.service;

import com.crafting.model.Item;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.RecipeOptionalIngredient;
import com.crafting.model.RecipeOptionalIngredientGroup;
import com.crafting.model.dto.ProfitEstimateDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProfitCalculationService {

    private static final Logger log = LoggerFactory.getLogger(ProfitCalculationService.class);

    private static final double AUCTION_HOUSE_FEE = 0.05d;

        public record CostBreakdown(
            long baseMaterialsCost,
            long optionalReagentsCost,
            List<Long> missingPrices
        ) {
        }

    /**
     * Base profit calculation — no character stat modifiers.
     */
    public ProfitEstimateDTO calculate(Recipe recipe) {
        return calculate(recipe, 0f, 0f);
    }

    /**
     * Adjusted profit calculation that accounts for character multicraft/resourcefulness
     * stats and the recipe's own multiplier settings.
     *
     * <ul>
     *   <li>Multicraft: only applies when {@code recipe.isMulticraftable()} is true.
     *       Expected yield multiplier = {@code 1 + (multicraftPercent/100) × recipe.multicraftMultiplier}.</li>
     *   <li>Resourcefulness: reduces cost of <b>non-optional</b> reagents only.
     *       Expected cost = {@code baseCost × (1 - (resourcefulnessPercent/100) × recipe.resourcefulnessFactor) + optionalCost}.</li>
     * </ul>
     */
    public ProfitEstimateDTO calculate(Recipe recipe, float multicraftPercent, float resourcefulnessPercent) {
        Set<Long> missingPriceSet = new LinkedHashSet<>();

        if (log.isDebugEnabled()) {
            log.debug("Calculating profit for recipeId={} name='{}' multicraftPercent={} resourcefulnessPercent={} outputItemId={} outputQuantity={} ingredientCount={} optionalGroupCount={} multicraftable={} multicraftMultiplier={} resourcefulnessFactor={}",
                    recipe.getId(),
                    recipe.getName(),
                    multicraftPercent,
                    resourcefulnessPercent,
                    recipe.getOutputItem() != null ? recipe.getOutputItem().getId() : null,
                    recipe.getOutputQuantity(),
                    recipe.getIngredients() != null ? recipe.getIngredients().size() : 0,
                    recipe.getOptionalIngredientGroups() != null ? recipe.getOptionalIngredientGroups().size() : 0,
                    recipe.isMulticraftable(),
                    recipe.getMulticraftMultiplier(),
                    recipe.getResourcefulnessFactor());
        }

        // ── Revenue ──────────────────────────────────────────────────────
        Long outputUnitPrice = resolveEffectivePrice(recipe.getOutputItem());
        float outputQuantity = recipe.getOutputQuantity() != null ? recipe.getOutputQuantity() : 1.0f;

        double yieldMultiplier = 1.0d;
        if (recipe.isMulticraftable() && multicraftPercent > 0) {
            float M = recipe.getMulticraftMultiplier() != null ? recipe.getMulticraftMultiplier() : 1.25f;
            yieldMultiplier = 1.0d + (multicraftPercent / 100.0d) * M;
        }

        long outputRevenue = 0L;
        if (outputUnitPrice == null) {
            missingPriceSet.add(recipe.getOutputItem().getId());
        } else {
            outputRevenue = Math.round(outputUnitPrice * outputQuantity * yieldMultiplier * (1.0d - AUCTION_HOUSE_FEE));
        }

        CostBreakdown costBreakdown = calculateCostBreakdown(recipe);
        missingPriceSet.addAll(costBreakdown.missingPrices());

        // ── Apply resourcefulness to base cost ──────────────────────────
        double adjustedBaseCost = costBreakdown.baseMaterialsCost();
        if (resourcefulnessPercent > 0) {
            float R = recipe.getResourcefulnessFactor() != null ? recipe.getResourcefulnessFactor() : 0.3f;
            adjustedBaseCost = costBreakdown.baseMaterialsCost() * (1.0d - (resourcefulnessPercent / 100.0d) * R);
        }

        long totalCost = Math.round(adjustedBaseCost) + costBreakdown.optionalReagentsCost();
        long profit = outputRevenue - totalCost;
        List<Long> missingPrices = List.copyOf(missingPriceSet);
        boolean calculable = missingPrices.isEmpty();

        log.debug("Calculated profit for recipeId={} outputRevenue={} baseCost={} adjustedBaseCost={} optionalCost={} totalCost={} profit={} calculable={} missingPrices={}",
            recipe.getId(),
            outputRevenue,
            costBreakdown.baseMaterialsCost(),
            Math.round(adjustedBaseCost),
            costBreakdown.optionalReagentsCost(),
            totalCost,
            profit,
            calculable,
            missingPrices);

        return new ProfitEstimateDTO(
                outputRevenue,
                totalCost,
                profit,
                AUCTION_HOUSE_FEE,
                missingPrices,
                calculable
        );
    }

    public CostBreakdown calculateCostBreakdown(Recipe recipe) {
        Set<Long> missingPriceSet = new LinkedHashSet<>();
        long baseCost = 0L;

        if (recipe.getIngredients() != null) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                Long ingredientPrice = resolveEffectivePrice(ingredient.getItem());
                if (ingredientPrice == null) {
                    missingPriceSet.add(ingredient.getItem().getId());
                    log.debug("Missing price for required ingredient itemId={} recipeId={}", ingredient.getItem().getId(), recipe.getId());
                    continue;
                }

                long lineCost = ingredientPrice * ingredient.getQuantity();
                baseCost += lineCost;
                log.debug("Required ingredient recipeId={} itemId={} unitPrice={} quantity={} lineCost={} runningBaseCost={}",
                        recipe.getId(),
                        ingredient.getItem().getId(),
                        ingredientPrice,
                        ingredient.getQuantity(),
                        lineCost,
                        baseCost);
            }
        }

        long optionalCost = 0L;
        if (recipe.getOptionalIngredientGroups() != null) {
            for (RecipeOptionalIngredientGroup group : recipe.getOptionalIngredientGroups()) {
                if (log.isDebugEnabled()) {
                    log.debug("Optional ingredient group recipeId={} groupId={} slotIndex={} label='{}' optionCount={}",
                            recipe.getId(),
                            group.getId(),
                            group.getSlotIndex(),
                            group.getLabel(),
                            group.getOptions() != null ? group.getOptions().size() : 0);
                }

                if (group.getOptions() == null) continue;
                for (RecipeOptionalIngredient option : group.getOptions()) {
                    Long optionPrice = resolveEffectivePrice(option.getItem());
                    if (optionPrice == null) {
                        missingPriceSet.add(option.getItem().getId());
                        log.debug("Missing price for optional ingredient itemId={} recipeId={} groupId={}",
                                option.getItem().getId(),
                                recipe.getId(),
                                group.getId());
                        continue;
                    }

                    long lineCost = optionPrice * option.getQuantity();
                    optionalCost += lineCost;
                    log.debug("Optional ingredient option recipeId={} groupId={} itemId={} unitPrice={} quantity={} lineCost={} runningOptionalCost={}",
                            recipe.getId(),
                            group.getId(),
                            option.getItem().getId(),
                            optionPrice,
                            option.getQuantity(),
                            lineCost,
                            optionalCost);
                }
            }
        }

        return new CostBreakdown(baseCost, optionalCost, List.copyOf(missingPriceSet));
    }

    public static Long resolvePrice(Item item) {
        if (item == null) {
            return null;
        }
        Long ahPrice = item.getCurrentPrice();
        Long vendorPrice = Boolean.TRUE.equals(item.getVendorItem()) ? item.getVendorPrice() : null;

        if (ahPrice != null && vendorPrice != null) {
            return Math.min(ahPrice, vendorPrice);
        }
        if (ahPrice != null) {
            return ahPrice;
        }
        return vendorPrice;
    }

    private Long resolveEffectivePrice(Item item) {
        return resolvePrice(item);
    }
}
