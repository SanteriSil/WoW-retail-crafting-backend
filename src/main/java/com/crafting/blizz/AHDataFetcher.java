package com.crafting.blizz;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crafting.repository.ItemRepository;
import com.crafting.model.Item;


@Service
@EnableConfigurationProperties(BlizzConfig.class)
public class AHDataFetcher {
    private final BlizzConfig blizzConfig;
    private final TokenService tokenService;
    private final BlizzApiClient blizzApiClient;
    private final AuctionProcesser auctionProcesser;
    private static final String BASE_URL = "https://eu.api.blizzard.com/data/wow/auctions/commodities";
    private static final String TOKEN_URL = "https://oauth.battle.net/token";
    private String clientId;
    private String clientSecret;
    private final ItemRepository itemRepository;


    public AHDataFetcher(BlizzConfig blizzConfig, TokenService tokenService,
                        BlizzApiClient blizzApiClient, AuctionProcesser auctionProcesser, ItemRepository itemRepository) {
        this.blizzConfig = blizzConfig;
        this.tokenService = tokenService;
        this.blizzApiClient = blizzApiClient;
        this.auctionProcesser = auctionProcesser;
        this.itemRepository = itemRepository;
    }

    //gets item IDs from repo
    //set unnecesary since IDs should be unique
    //change later
    private HashSet<Integer> fetchDbItemIds() {
        List<Item> items = itemRepository.findAll();
        HashSet<Integer> itemIds = new HashSet<>();
        for (Item item : items) {
            itemIds.add(item.getId().intValue());
        }
        return itemIds;
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