package com.crafting.model.dto;

import java.util.List;

public record DashboardResponse(
        List<DashboardCraft> crafts,
        long totalBaseProfit,
        long totalAdjustedProfit,
        int totalCrafts
) {

    public record DashboardCraft(
            Long characterId,
            String characterName,
            String characterIconUrl,
            Long recipeId,
            String recipeName,
            Integer professionId,
            String professionName,
            Long outputItemId,
            String outputItemName,
            Float outputQuantity,
            ProfitEstimateDTO baseProfit,
            ProfitEstimateDTO adjustedProfit,
            boolean isMulticraftable,
            Float multicraftMultiplier,
            Float resourcefulnessFactor,
            Float multicraftPercent,
            Float resourcefulnessPercent,
            List<Long> missingPriceItemIds,
            boolean hasNotes
    ) {
    }
}
