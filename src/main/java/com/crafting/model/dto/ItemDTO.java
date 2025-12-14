package com.crafting.model.dto;

import java.time.OffsetDateTime;

public class ItemDTO {

    private final Long id;
    private final String name;

    private final Integer professionId;
    private final String professionName;

    private final Short quality;

    private final Long currentPrice;
    private final OffsetDateTime currentPriceRecordedAt;

    public ItemDTO(
        Long id,
        String name,
        Integer professionId,
        String professionName,
        Short quality,
        Long currentPrice,
        OffsetDateTime currentPriceRecordedAt
    ) {
        this.id = id;
        this.name = name;
        this.professionId = professionId;
        this.professionName = professionName;
        this.quality = quality;
        this.currentPrice = currentPrice;
        this.currentPriceRecordedAt = currentPriceRecordedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getProfessionId() {
        return professionId;
    }

    public String getProfessionName() {
        return professionName;
    }

    public Short getQuality() {
        return quality;
    }

    public Long getCurrentPrice() {
        return currentPrice;
    }

    public OffsetDateTime getCurrentPriceRecordedAt() {
        return currentPriceRecordedAt;
    }
}
