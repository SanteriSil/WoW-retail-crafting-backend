package com.crafting.blizz;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;


@Service
public class TokenService {
    private static final String TOKEN_URL = "https://oauth.battle.net/token";
    private final RestTemplate rest = new RestTemplate();

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public String getAccessToken(String clientId, String clientSecret) {
        if (token == null || Instant.now().isAfter(expiresAt.minusSeconds(30))) {
            refreshToken(clientId, clientSecret);
        }
        return token;
    }

    private void refreshToken(String clientId, String clientSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, clientSecret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        ResponseEntity<TokenResponse> resp = rest.postForEntity(
            TOKEN_URL,
            new HttpEntity<>(body, headers),
            TokenResponse.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("Failed to obtain token: " + resp);
        }
        TokenResponse tokenResponse = resp.getBody();
        this.token = tokenResponse.getAccessToken();
        this.expiresAt = Instant.now()
                    .plusSeconds(Math.max(30, tokenResponse.getExpiresIn()));
    }
}
