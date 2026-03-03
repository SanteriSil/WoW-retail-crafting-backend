package com.crafting.model.dto;

import java.time.OffsetDateTime;

public record RecipeSummaryDTO(
        Long id,
        String name,
        Long wowheadSpellId,
        Long outputItemId,
        String outputItemName,
        Float outputQuantity,
        Integer professionId,
        String professionName,
        Integer expansionId,
        String expansionName,
        String source,
        Long estimatedProfit,
        boolean profitCalculable,
        boolean hasNotes,
        OffsetDateTime updatedAt
) {
}
