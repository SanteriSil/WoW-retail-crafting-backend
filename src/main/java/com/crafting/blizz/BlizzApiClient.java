package com.crafting.blizz;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BlizzApiClient {
    private static final String BASE_URL = "https://eu.api.blizzard.com/data/wow/auctions/commodities";
    private final RestTemplate rest = new RestTemplate();

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
}
