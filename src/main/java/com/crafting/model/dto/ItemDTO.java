package com.crafting.model.dto;

import java.time.OffsetDateTime;

import lombok.Value;

@Value
public class ItemDTO {
    Long id;
    String name;
    Integer professionId;
    String professionName;
    Short quality;
    boolean finishingIngredient;
    Long currentPrice;
    OffsetDateTime currentPriceRecordedAt;
}
