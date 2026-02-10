package com.crafting.blizz;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.http.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;


@Service
@EnableConfigurationProperties(BlizzConfig.class)
public class AHDataFetcher {
    private final BlizzConfig blizzConfig;
    private final TokenService tokenService;
    private final BlizzApiClient blizzApiClient;
    private final AuctionProcesser auctionProcesser;
    private String clientId;
    private String clientSecret;


    public AHDataFetcher(BlizzConfig blizzConfig, TokenService tokenService,
                        BlizzApiClient blizzApiClient, AuctionProcesser auctionProcesser) {
        this.blizzConfig = blizzConfig;
        this.tokenService = tokenService;
        this.blizzApiClient = blizzApiClient;
        this.auctionProcesser = auctionProcesser;
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
            String accessToken = tokenService.getAccessToken(clientId, clientSecret);
            ResponseEntity<String> resp = blizzApiClient.fetchCommodities(accessToken);
            System.out.println("API response status: " + resp.getStatusCode());
            String body = resp.getBody();
            if (body != null) {
                // Collect matching auctions
                Map<Integer, List<AuctionEntry>> matches = auctionProcesser.processAndCollect(
                    body,
                    fetchDbItemIds()
                );
                System.out.println("Matching auctions: " + matches);
                // Calculate average prices
                Map<Integer, Long> avgPrices = auctionProcesser.calculateAveragePrices(matches);
                System.out.println("Average prices: " + avgPrices);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @PostConstruct
    public void init() {
        clientId = blizzConfig.getClientId();
        clientSecret = blizzConfig.getClientSecret();
        // for quick testing; remove or schedule in production
        callApi();
    }
}
