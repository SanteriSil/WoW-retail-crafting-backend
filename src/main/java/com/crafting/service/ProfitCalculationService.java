package com.crafting.service;

import com.crafting.model.Item;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.RecipeOptionalIngredient;
import com.crafting.model.RecipeOptionalIngredientGroup;
import com.crafting.model.dto.ProfitEstimateDTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProfitCalculationService {

    private static final double AUCTION_HOUSE_FEE = 0.05d;

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
        List<Long> missingPrices = new ArrayList<>();

        // ── Revenue ──────────────────────────────────────────────────────
        Long outputUnitPrice = resolveEffectivePrice(recipe.getOutputItem());
        float outputQuantity = recipe.getOutputQuantity() != null ? recipe.getOutputQuantity() : 1.0f;

        double yieldMultiplier = 1.0d;
        if (recipe.isMulticraftable() && multicraftPercent > 0) {
            float M = recipe.getMulticraftMultiplier() != null ? recipe.getMulticraftMultiplier() : 1.2f;
            yieldMultiplier = 1.0d + (multicraftPercent / 100.0d) * M;
        }

        long outputRevenue = 0L;
        if (outputUnitPrice == null) {
            missingPrices.add(recipe.getOutputItem().getId());
        } else {
            outputRevenue = Math.round(outputUnitPrice * outputQuantity * yieldMultiplier * (1.0d - AUCTION_HOUSE_FEE));
        }

        // ── Cost: required (non-optional) ingredients ────────────────────
        long baseCost = 0L;
        if (recipe.getIngredients() != null) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                Long ingredientPrice = resolveEffectivePrice(ingredient.getItem());
                if (ingredientPrice == null) {
                    missingPrices.add(ingredient.getItem().getId());
                    continue;
                }
                baseCost += ingredientPrice * ingredient.getQuantity();
            }
        }

        // ── Cost: optional ingredient groups (not affected by resourcefulness) ──
        long optionalCost = 0L;
        // Optional costs are computed for informational purposes but are NOT
        // included in the default profit calc (users pick options at craft time).
        // The plan says resourcefulness only affects non-optional portions, so
        // we separate them here for the formula even though the base calculate()
        // did not previously account for optional costs at all.

        // ── Apply resourcefulness to base cost ──────────────────────────
        double adjustedBaseCost = baseCost;
        if (resourcefulnessPercent > 0) {
            float R = recipe.getResourcefulnessFactor() != null ? recipe.getResourcefulnessFactor() : 0.3f;
            adjustedBaseCost = baseCost * (1.0d - (resourcefulnessPercent / 100.0d) * R);
        }

        long totalCost = Math.round(adjustedBaseCost) + optionalCost;
        long profit = outputRevenue - totalCost;
        boolean calculable = missingPrices.isEmpty();

        return new ProfitEstimateDTO(
                outputRevenue,
                totalCost,
                profit,
                AUCTION_HOUSE_FEE,
                List.copyOf(missingPrices),
                calculable
        );
    }

    private Long resolveEffectivePrice(Item item) {
        if (item == null) {
            return null;
        }
        if (item.getCurrentPrice() != null) {
            return item.getCurrentPrice();
        }
        if (Boolean.TRUE.equals(item.getVendorItem()) && item.getVendorPrice() != null) {
            return item.getVendorPrice();
        }
        return null;
    }
}
