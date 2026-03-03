package com.crafting.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CharacterDTO(
        Long id,
        String name,
        String realm,
        String iconUrl,
        List<ProfessionView> professions,
        int assignedRecipeCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public record ProfessionView(
            Long id,
            Integer professionId,
            String professionName,
            Float multicraftPercent,
            Float resourcefulnessPercent
    ) {
    }
}
