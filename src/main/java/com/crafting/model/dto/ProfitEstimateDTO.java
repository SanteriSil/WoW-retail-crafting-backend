package com.crafting.model.dto;

import java.util.List;

public record ProfitEstimateDTO(
        long outputRevenue,
        long ingredientCost,
        long profit,
        double auctionHouseFee,
        List<Long> missingPrices,
        boolean calculable
) {
}
