package com.crafting.service;

import com.crafting.model.Item;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.dto.ProfitEstimateDTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProfitCalculationService {

    private static final double AUCTION_HOUSE_FEE = 0.05d;

    public ProfitEstimateDTO calculate(Recipe recipe) {
        List<Long> missingPrices = new ArrayList<>();

        Long outputUnitPrice = resolveEffectivePrice(recipe.getOutputItem());
        float outputQuantity = recipe.getOutputQuantity() != null ? recipe.getOutputQuantity() : 1.0f;

        long outputRevenue = 0L;
        if (outputUnitPrice == null) {
            missingPrices.add(recipe.getOutputItem().getId());
        } else {
            outputRevenue = Math.round(outputUnitPrice * outputQuantity * (1.0d - AUCTION_HOUSE_FEE));
        }

        long ingredientCost = 0L;
        if (recipe.getIngredients() != null) {
            for (RecipeIngredient ingredient : recipe.getIngredients()) {
                Long ingredientPrice = resolveEffectivePrice(ingredient.getItem());
                if (ingredientPrice == null) {
                    missingPrices.add(ingredient.getItem().getId());
                    continue;
                }
                ingredientCost += ingredientPrice * ingredient.getQuantity();
            }
        }

        long profit = outputRevenue - ingredientCost;
        boolean calculable = missingPrices.isEmpty();

        return new ProfitEstimateDTO(
                outputRevenue,
                ingredientCost,
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
