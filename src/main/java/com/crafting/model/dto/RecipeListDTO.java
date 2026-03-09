package com.crafting.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RecipeListDTO(
        Long id,
        String name,
        List<RecipeListEntryDTO> recipes,
        int recipeCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record RecipeListEntryDTO(
            Long recipeId,
            String recipeName,
            Long outputItemId,
            String outputItemName
    ) {
    }
}
