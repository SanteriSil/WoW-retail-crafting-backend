package com.crafting.model.dto;

import java.util.Set;

public record RecipeListItemIdsDTO(
        Long listId,
        String listName,
        Set<Long> ingredientItemIds,
        Set<Long> outputItemIds,
        Set<Long> allItemIds,
        Set<Long> blacklistedItemIds
) {
}
