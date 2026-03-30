package com.crafting.model.dto;

import java.util.List;

public record DashboardResponse(
        List<DashboardCraft> crafts,
        long totalBaseCost,
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
            Long outputItemPrice,
            Float outputQuantity,
            Short outputItemQuality,
            ProfitEstimateDTO baseProfit,
            ProfitEstimateDTO adjustedProfit,
            Long baseMaterialsCost,
            Long optionalReagentsCost,
            boolean isMulticraftable,
            Float multicraftMultiplier,
            Float resourcefulnessFactor,
                        Float baseMulticraftPercent,
                        Float baseResourcefulnessPercent,
            Float multicraftPercent,
            Float resourcefulnessPercent,
                        boolean statOverrideActive,
            List<Long> missingPriceItemIds,
            boolean hasNotes,
            String notes
    ) {
    }
}
