package com.crafting.model.dto;

import java.time.OffsetDateTime;

public record RecipeSummaryDTO(
        Long id,
        String name,
        Long wowheadSpellId,
        Long outputItemId,
        String outputItemName,
        Float outputQuantity,
        Short outputItemQuality,
        Integer professionId,
        String professionName,
        Integer expansionId,
        String expansionName,
        String source,
        Long estimatedProfit,
        boolean profitCalculable,
        boolean multicraftable,
        Float multicraftMultiplier,
        Float resourcefulnessFactor,
        boolean hasNotes,
        OffsetDateTime updatedAt
) {
}
