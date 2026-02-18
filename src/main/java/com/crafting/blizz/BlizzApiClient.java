package com.crafting.blizz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class BlizzApiClient {
    private static final String BASE_URL = "https://eu.api.blizzard.com/data/wow/auctions/commodities";
    private static final String ITEM_MEDIA_URL = "https://eu.api.blizzard.com/data/wow/media/item/";
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResponseEntity<String> fetchCommodities(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> req = new HttpEntity<>(headers);
        ResponseEntity<String> resp = rest.exchange(
            BASE_URL + "?namespace=dynamic-eu&locale=en_GB",
            HttpMethod.GET,
            req,
            String.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("Failed to fetch commodities: " + resp.getStatusCode());
        }
        return resp;
    }

    public Optional<String> fetchItemIconUrl(Long itemId, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> req = new HttpEntity<>(headers);

        ResponseEntity<String> resp = rest.exchange(
            ITEM_MEDIA_URL + itemId + "?namespace=static-eu&locale=en_GB",
            HttpMethod.GET,
            req,
            String.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("Failed to fetch item media for " + itemId + ": " + resp.getStatusCode());
        }

        try {
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode assets = root.path("assets");
            if (assets.isArray()) {
                for (JsonNode asset : assets) {
                    String key = asset.path("key").asText("");
                    String value = asset.path("value").asText("");
                    if ("icon".equalsIgnoreCase(key) && !value.isBlank()) {
                        return Optional.of(value);
                    }
                }

                for (JsonNode asset : assets) {
                    String value = asset.path("value").asText("");
                    if (!value.isBlank()) {
                        return Optional.of(value);
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse item media response for " + itemId, e);
        }
    }
}
