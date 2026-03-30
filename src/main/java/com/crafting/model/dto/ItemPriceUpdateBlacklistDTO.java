package com.crafting.model.dto;

import java.time.OffsetDateTime;

public record ItemPriceUpdateBlacklistDTO(
        Long listId,
        Long itemId,
        String itemName,
        OffsetDateTime createdAt
) {
}
