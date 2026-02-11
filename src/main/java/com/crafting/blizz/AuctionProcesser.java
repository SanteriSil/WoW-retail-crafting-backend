package com.crafting.blizz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuctionProcesser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<Integer, List<AuctionEntry>> processAndCollect(String body, Set<Integer> dbIds) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode auctions = root.path("auctions");
        Map<Integer, List<AuctionEntry>> result = new HashMap<>();
        if (!auctions.isArray()) return result;

        for (JsonNode a : auctions) {
            int itemId = a.path("item").path("id").asInt(0);
            if (itemId == 0 || !dbIds.contains(itemId)) continue;
            long unitPrice = a.path("unit_price").asLong(0);
            int quantity = a.path("quantity").asInt(0);
            result.computeIfAbsent(itemId, k -> new ArrayList<>())
                    .add(new AuctionEntry(unitPrice, quantity));
        }
        return result;
    }

    public Map<Integer, Long> calculateAveragePrices(Map<Integer, List<AuctionEntry>> auctions) {
        /* The list of action entries is first sorted by unit price from lowest
        to highest. */
        Map<Integer, Long> averagePrices = new HashMap<>();
        for (Map.Entry<Integer, List<AuctionEntry>> entry : auctions.entrySet()) {
            List<AuctionEntry> auctionEntries = entry.getValue();

            if (auctionEntries.isEmpty()) continue; // Avoid division by zero

            auctionEntries.sort((a, b) -> Long.compare(a.getUnitPrice(), b.getUnitPrice()));

            int totalEntries = auctionEntries.size();
            /* The top 20% of the entries (rounded up) are selected. */
            int selectedCount = Math.max(1, (int) Math.ceil(totalEntries * 0.2));
            int toDouble = selectedCount / 2; // First half will be doubled

            long totalQty = 0;
            long weightedSum = 0;

            for (int i = 0; i < selectedCount; i++) {
                AuctionEntry ae = auctionEntries.get(i);
                long qty = ae.getQuantity();

                if (i < toDouble) {
                    qty *= 2; // Double the quantity for the first half
                }
                totalQty += qty;
                weightedSum += ae.getUnitPrice() * qty;
            }
            if (totalQty > 0) {
                averagePrices.put(entry.getKey(), weightedSum / totalQty);
            }
        }
        return averagePrices;
    }
}
