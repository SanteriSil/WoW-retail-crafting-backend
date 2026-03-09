package com.crafting.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RecipeDTO(
        Long id,
        String name,
        Long wowheadSpellId,
        ItemView outputItem,
        Float outputQuantity,
        ProfessionView profession,
        ExpansionView expansion,
        String source,
        List<IngredientView> ingredients,
        List<OptionalIngredientGroupView> optionalIngredientGroups,
        ProfitEstimateDTO profitEstimate,
        boolean multicraftable,
        Float multicraftMultiplier,
        Float resourcefulnessFactor,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public record ItemView(
            Long id,
            String name,
            Long currentPrice,
            String iconUrl,
            Short quality
    ) {
    }

    public record ProfessionView(
            Integer id,
            String name
    ) {
    }

    public record ExpansionView(
            Integer id,
            String name,
            String slug
    ) {
    }

    public record IngredientView(
            Long id,
            ItemView item,
            Long itemPrice,
            Integer quantity
    ) {
    }

    public record OptionalIngredientGroupView(
            Long id,
            Short slotIndex,
            String label,
            List<OptionalIngredientOptionView> options
    ) {
    }

    public record OptionalIngredientOptionView(
            Long id,
            ItemView item,
            Integer quantity
    ) {
    }
}
