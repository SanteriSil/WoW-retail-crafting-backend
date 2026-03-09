package com.crafting.service;

import com.crafting.model.Item;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.RecipeOptionalIngredient;
import com.crafting.model.RecipeOptionalIngredientGroup;
import com.crafting.model.dto.ProfitEstimateDTO;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProfitCalculationService {

    private static final Logger log = LoggerFactory.getLogger(ProfitCalculationService.class);

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
                    log.debug("Missing price for required ingredient itemId={} recipeId={}", ingredient.getItem().getId(), recipe.getId());
                    continue;
                }
                baseCost += ingredientPrice * ingredient.getQuantity();
                log.debug("Required ingredient recipeId={} itemId={} unitPrice={} quantity={} lineCost={} runningBaseCost={}",
                        recipe.getId(),
                        ingredient.getItem().getId(),
                        ingredientPrice,
                        ingredient.getQuantity(),
                        ingredientPrice * ingredient.getQuantity(),
                        baseCost);
            }
        }

        // ── Cost: optional ingredient groups (not affected by resourcefulness) ──
        long optionalCost = 0L;
        if (recipe.getOptionalIngredientGroups() != null && log.isDebugEnabled()) {
            for (RecipeOptionalIngredientGroup group : recipe.getOptionalIngredientGroups()) {
                log.debug("Optional ingredient group recipeId={} groupId={} slotIndex={} label='{}' optionCount={}",
                        recipe.getId(),
                        group.getId(),
                        group.getSlotIndex(),
                        group.getLabel(),
                        group.getOptions() != null ? group.getOptions().size() : 0);
                if (group.getOptions() != null) {
                    for (RecipeOptionalIngredient option : group.getOptions()) {
                        Long optionPrice = resolveEffectivePrice(option.getItem());
                        log.debug("Optional ingredient option recipeId={} groupId={} itemId={} unitPrice={} quantity={}",
                                recipe.getId(),
                                group.getId(),
                                option.getItem() != null ? option.getItem().getId() : null,
                                optionPrice,
                                option.getQuantity());
                    }
                }
            }
        }
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

        log.debug("Calculated profit for recipeId={} outputRevenue={} baseCost={} adjustedBaseCost={} optionalCost={} totalCost={} profit={} calculable={} missingPrices={}",
            recipe.getId(),
            outputRevenue,
            baseCost,
            Math.round(adjustedBaseCost),
            optionalCost,
            totalCost,
            profit,
            calculable,
            missingPrices);

        return new ProfitEstimateDTO(
                outputRevenue,
                totalCost,
                profit,
                AUCTION_HOUSE_FEE,
                List.copyOf(missingPrices),
                calculable
        );
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
