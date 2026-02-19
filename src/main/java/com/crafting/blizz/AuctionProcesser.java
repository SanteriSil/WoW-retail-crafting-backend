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
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuctionProcesser.class);

    /**
     * Parses addon collected auction data:
     *   itemId,unitPrice,quantity
     * Blank lines and malformed rows are skipped with a warning.
     */
    public Map<Integer, List<AuctionEntry>> parseCsvAuctions(String csv, Set<Integer> dbIds) {
        Map<Integer, List<AuctionEntry>> result = new HashMap<>();
        if (csv == null || csv.isBlank()) return result;

        String[] lines = csv.split("\\r?\\n");
        int skipped = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String[] parts = trimmed.split(",");
            if (parts.length < 3) {
                skipped++;
                continue;
            }
            try {
                int itemId = Integer.parseInt(parts[0].trim());
                long unitPrice = Long.parseLong(parts[1].trim());
                int quantity = Integer.parseInt(parts[2].trim());
                if (itemId == 0 || !dbIds.contains(itemId)) continue;
                result.computeIfAbsent(itemId, k -> new ArrayList<>())
                        .add(new AuctionEntry(unitPrice, quantity));
            } catch (NumberFormatException e) {
                skipped++;
            }
        }
        if (skipped > 0) {
            logger.warn("Skipped {} bad CSV auction lines", skipped);
        }
        logger.debug("Parsed {} auction entries for {} items from CSV",
            result.values().stream().mapToInt(List::size).sum(), result.size());
        return result;
    }

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
