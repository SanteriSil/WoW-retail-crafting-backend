package com.crafting.auth;

import com.crafting.repository.AllowedUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class DiscordOAuthService {

    private static final Logger log = LoggerFactory.getLogger(DiscordOAuthService.class);
    private static final String TOKEN_URL = "https://discord.com/api/v10/oauth2/token";
    private static final String USER_URL = "https://discord.com/api/v10/users/@me";

    private final String clientId;
    private final String clientSecret;
    private final JwtService jwtService;
    private final AllowedUserRepository allowedUserRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DiscordOAuthService(
            @Value("${discord.clientId}") String clientId,
            @Value("${discord.clientSecret}") String clientSecret,
            JwtService jwtService,
            AllowedUserRepository allowedUserRepository) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.jwtService = jwtService;
        this.allowedUserRepository = allowedUserRepository;
    }

    /**
     * Full OAuth flow: exchange code → fetch user → check allowlist → return JWT + user info.
     */
    public AuthResult handleCallback(String code, String redirectUri) {
        try {
            // 1. Exchange code for access token
            String accessToken = exchangeCode(code, redirectUri);

            // 2. Fetch Discord user info
            JsonNode user = fetchDiscordUser(accessToken);
            long discordId = user.get("id").asLong();
            String username = user.get("username").asText();
            String avatarHash = user.has("avatar") && !user.get("avatar").isNull()
                    ? user.get("avatar").asText() : null;
            String avatarUrl = avatarHash != null
                    ? "https://cdn.discordapp.com/avatars/" + discordId + "/" + avatarHash + ".png"
                    : null;

            log.info("Discord login attempt by {} ({})", username, discordId);

            // 3. Check allowlist
            if (!allowedUserRepository.existsById(discordId)) {
                log.warn("Discord user {} ({}) not in allowlist", username, discordId);
                throw new SecurityException("User not authorized");
            }

            // 4. Generate JWT
            String token = jwtService.generateToken(discordId, username);

            log.info("Discord user {} ({}) authenticated successfully", username, discordId);
            return new AuthResult(token, username, avatarUrl);

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Discord OAuth flow failed", e);
            throw new RuntimeException("Discord authentication failed: " + e.getMessage(), e);
        }
    }

    private String exchangeCode(String code, String redirectUri) throws IOException, InterruptedException {
        StringJoiner body = new StringJoiner("&");
        for (Map.Entry<String, String> entry : Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", redirectUri
        ).entrySet()) {
            body.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("Discord token exchange failed: {} {}", response.statusCode(), response.body());
            throw new RuntimeException("Discord token exchange failed (HTTP " + response.statusCode() + ")");
        }

        JsonNode json = objectMapper.readTree(response.body());
        return json.get("access_token").asText();
    }

    private JsonNode fetchDiscordUser(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USER_URL))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("Discord user fetch failed: {} {}", response.statusCode(), response.body());
            throw new RuntimeException("Failed to fetch Discord user (HTTP " + response.statusCode() + ")");
        }

        return objectMapper.readTree(response.body());
    }

    public record AuthResult(String token, String discordUsername, String avatarUrl) {}
}
