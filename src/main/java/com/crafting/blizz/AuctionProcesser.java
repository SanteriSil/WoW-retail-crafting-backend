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
}
