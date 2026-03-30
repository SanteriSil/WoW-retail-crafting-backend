package com.crafting.model.dto;

import java.time.OffsetDateTime;

public record PriceSubmissionDTO(
        Long id,
        Long itemId,
        String itemName,
        Long submittedPrice,
        Long submittedQuantity,
        String source,
        Long actorDiscordId,
        String actorDiscordUsername,
        Long auditEventId,
        String batchId,
        OffsetDateTime submittedAt
) {
}
