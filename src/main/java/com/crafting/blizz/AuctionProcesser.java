package com.crafting.blizz;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuctionProcesser {
    private static final long MAX_QUANTITY_FOR_AVERAGE = 5000;

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

    /**
     * Streaming version of processAndCollect that reads from an InputStream
     * using Jackson's streaming parser. Only one auction entry is in memory at a
     * time, so this can handle responses of any size without OOM.
     */
    public Map<Integer, List<AuctionEntry>> processAndCollectStreaming(InputStream inputStream, Set<Integer> dbIds) throws IOException {
        Map<Integer, List<AuctionEntry>> result = new HashMap<>();

        try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
            // Navigate to the "auctions" array
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "auctions".equals(parser.currentName())) {
                    if (parser.nextToken() != JsonToken.START_ARRAY) {
                        logger.warn("Expected auctions array but found: {}", parser.currentToken());
                        return result;
                    }
                    break;
                }
            }

            // Process each auction entry individually — only matched items stay in memory
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode auction = objectMapper.readTree(parser);
                int itemId = auction.path("item").path("id").asInt(0);
                if (itemId != 0 && dbIds.contains(itemId)) {
                    long unitPrice = auction.path("unit_price").asLong(0);
                    int quantity = auction.path("quantity").asInt(0);
                    result.computeIfAbsent(itemId, k -> new ArrayList<>())
                            .add(new AuctionEntry(unitPrice, quantity));
                }
            }
        }

        logger.debug("Streaming parse complete: matched {} items with {} total entries",
            result.size(), result.values().stream().mapToInt(List::size).sum());
        return result;
    }

    public Map<Integer, Pair<Long, Long>> calculateAveragePrices(Map<Integer, List<AuctionEntry>> auctions) {
        /* The list of action entries is first sorted by unit price from lowest
        to highest. */
        Map<Integer, Pair<Long, Long>> averagePrices = new HashMap<>();
        for (Map.Entry<Integer, List<AuctionEntry>> entry : auctions.entrySet()) {
            List<AuctionEntry> auctionEntries = entry.getValue();

            if (auctionEntries.isEmpty()) continue; // Avoid division by zero

            auctionEntries.sort((a, b) -> Long.compare(a.getUnitPrice(), b.getUnitPrice()));

            int totalEntries = auctionEntries.size();
            long totalQuantity = 0;
            for (AuctionEntry ae : auctionEntries) {
                totalQuantity += Math.max(0, ae.getQuantity());
            }
            if (totalQuantity <= 0) continue;

            /* Select from lowest-priced entries until reaching 20% of total quantity, capped at 5000 units. */
            long selectedQuantityByPercent = Math.max(1L, (long) Math.ceil(totalQuantity * 0.2d));
            long quantityTarget = Math.min(selectedQuantityByPercent, MAX_QUANTITY_FOR_AVERAGE);
            long[] quantitiesUsed = new long[totalEntries];

            long remainingQuantity = quantityTarget;
            int effectiveSelectedEntries = 0;
            for (int i = 0; i < totalEntries && remainingQuantity > 0; i++) {
                AuctionEntry ae = auctionEntries.get(i);
                long entryQty = Math.max(0, ae.getQuantity());
                if (entryQty == 0) {
                    continue;
                }

                long qtyToUse = Math.min(entryQty, remainingQuantity);
                quantitiesUsed[i] = qtyToUse;
                remainingQuantity -= qtyToUse;
                effectiveSelectedEntries++;
            }

            int toDouble = effectiveSelectedEntries / 2; // First half of selected entries will be doubled

            long totalQty = 0;
            long weightedSum = 0;

            int selectedEntryIndex = 0;
            for (int i = 0; i < totalEntries; i++) {
                long qty = quantitiesUsed[i];
                if (qty <= 0) {
                    continue;
                }

                AuctionEntry ae = auctionEntries.get(i);

                if (selectedEntryIndex < toDouble) {
                    qty *= 2; // Double the quantity for the first half
                }
                totalQty += qty;
                weightedSum += ae.getUnitPrice() * qty;
                selectedEntryIndex++;
            }
            if (totalQty > 0) {
                Pair<Long, Long> priceAndQty = new Pair<>(weightedSum / totalQty, totalQuantity);
                averagePrices.put(entry.getKey(), priceAndQty);
            }
        }
        return averagePrices;
    }
}
