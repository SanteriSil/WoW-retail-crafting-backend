package com.crafting.model.dto;

import java.time.OffsetDateTime;

public record RecipeCharacterStatOverrideDTO(
        Long id,
        Long recipeId,
        String recipeName,
        Long characterId,
        String characterName,
        Float multicraftPercent,
        Float resourcefulnessPercent,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
