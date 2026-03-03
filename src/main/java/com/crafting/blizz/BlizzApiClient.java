package com.crafting.blizz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Service
public class BlizzApiClient {
    private static final Logger log = LoggerFactory.getLogger(BlizzApiClient.class);
    private static final String BASE_URL = "https://eu.api.blizzard.com/data/wow/auctions/commodities";
    private static final String ITEM_MEDIA_URL = "https://eu.api.blizzard.com/data/wow/media/item/";
    private static final String CHARACTER_MEDIA_URL = "https://eu.api.blizzard.com/profile/wow/character/%s/%s/character-media?namespace=profile-eu&locale=en_GB";
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

    /**
     * Streams the commodities response to avoid loading the entire (potentially 100MB+)
     * JSON payload into memory. The consumer receives the raw InputStream which must
     * be fully consumed before the method returns (connection closes afterwards).
     */
    public HttpStatusCode streamCommodities(String accessToken, StreamConsumer consumer) {
        return rest.execute(
            BASE_URL + "?namespace=dynamic-eu&locale=en_GB",
            HttpMethod.GET,
            request -> request.getHeaders().setBearerAuth(accessToken),
            response -> {
                HttpStatusCode status = response.getStatusCode();
                if (!status.is2xxSuccessful()) {
                    throw new IllegalStateException("Failed to fetch commodities: " + status);
                }
                consumer.accept(response.getBody());
                return status;
            }
        );
    }

    @FunctionalInterface
    public interface StreamConsumer {
        void accept(InputStream inputStream) throws IOException;
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

    /**
     * Fetches the avatar URL for a WoW character from the Blizzard Character Media API.
     * Best-effort: returns {@code Optional.empty()} if the character is not found, the
     * profile is private, or the API is unavailable.
     *
     * @param realmSlug     lowercase, hyphen-separated realm name (e.g. "argent-dawn")
     * @param characterName lowercase character name
     * @param accessToken   Blizzard OAuth client-credentials token
     */
    public Optional<String> fetchCharacterAvatar(String realmSlug, String characterName, String accessToken) {
        String url = String.format(CHARACTER_MEDIA_URL, realmSlug, characterName);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> req = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, req, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode assets = root.path("assets");
            if (assets.isArray()) {
                for (JsonNode asset : assets) {
                    if ("avatar".equalsIgnoreCase(asset.path("key").asText(""))) {
                        String value = asset.path("value").asText("");
                        if (!value.isBlank()) {
                            return Optional.of(value);
                        }
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch character avatar for {}/{}: {}", realmSlug, characterName, e.getMessage());
            return Optional.empty();
        }
    }
}
