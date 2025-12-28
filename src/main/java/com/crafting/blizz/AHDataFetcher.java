package com.crafting.blizz;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
@EnableConfigurationProperties(BlizzConfig.class)
public class AHDataFetcher {
    private final BlizzConfig blizzConfig;
    private static final String BASE_URL = "https://eu.api.blizzard.com/data/wow/auctions/commodities";
    private static final String TOKEN_URL = "https://oauth.battle.net/token";
    private String clientId;
    private String clientSecret;


    public AHDataFetcher(BlizzConfig blizzConfig) {
        this.blizzConfig = blizzConfig;
    }

    /* Placeholder function */
    private HashSet<Integer> fetchDbItemIds() {
        // In a real implementation, fetch item IDs from a database or another source
        HashSet<Integer> ids = new HashSet<>();
        ids.add(219946); // Example item ID
        ids.add(219949); // Example item ID
        return ids;
    }

    public void callApi() {
        if (clientId == null || clientSecret == null) {
            System.out.println("Missing clientId/secret - check env vars and application.properties");
            return;
        }

        try {
            String accessToken = fetchAccessToken(clientId, clientSecret);
            String apiUrl = BASE_URL + "?namespace=dynamic-eu&locale=en_GB";

            RestTemplate rest = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> req = new HttpEntity<>(headers);
            ResponseEntity<String> resp = rest.exchange(apiUrl, HttpMethod.GET, req, String.class);

            System.out.println("API response status: " + resp.getStatusCode());
            String body = resp.getBody();
            if (body != null) {
                // Collect matching auctions
                Map<Integer, List<AuctionEntry>> matches = processAndCollect(body);
                System.out.println("Matching auctions: " + matches);


            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<Integer, List<AuctionEntry>> processAndCollect(String body) throws IOException {
        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(body);
        JsonNode auctions = root.path("auctions");
        Map<Integer, List<AuctionEntry>> result = new HashMap<>();
        if (!auctions.isArray()) return result;

        Set<Integer> dbIds = fetchDbItemIds();

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

    private String fetchAccessToken(String clientId, String clientSecret) {
        RestTemplate rest = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<TokenResponse> resp = rest.postForEntity(TOKEN_URL, request, TokenResponse.class);

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("Failed to obtain token: " + resp);
        }
        return resp.getBody().getAccessToken();
    }

    @PostConstruct
    public void init() {
        clientId = blizzConfig.getClientId();
        clientSecret = blizzConfig.getClientSecret();
        // for quick testing; remove or schedule in production
        callApi();
    }

    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("expires_in")
        private int expiresIn;
        @JsonProperty("token_type")
        private String tokenType;
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public int getExpiresIn() { return expiresIn; }
        public void setExpiresIn(int expiresIn) { this.expiresIn = expiresIn; }
        public String getTokenType() { return tokenType; }
        public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    }
}