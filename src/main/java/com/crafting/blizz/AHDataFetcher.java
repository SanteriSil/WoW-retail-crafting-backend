package com.crafting.blizz;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;

import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.crafting.repository.ItemRepository;
import com.crafting.model.Item;


@Service
@EnableConfigurationProperties(BlizzConfig.class)
public class AHDataFetcher {
    private final BlizzConfig blizzConfig;
    private final TokenService tokenService;
    private final BlizzApiClient blizzApiClient;
    private final AuctionProcesser auctionProcesser;
    private String clientId;
    private String clientSecret;
    private final ItemRepository itemRepository;
    private final ReentrantLock fetchLock = new ReentrantLock();


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

    // runs every 20 minutes
    @Scheduled(cron = "0 */20 * * * *")
    public void callApi() {
        try {
            triggerFetch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    private void saveItemsToDb(Map<Integer, Long> avgPrices) {
        for (Map.Entry<Integer, Long> entry : avgPrices.entrySet()) {
            Integer itemId = entry.getKey();
            Long avgPrice = entry.getValue();
            Item item = itemRepository.findById(itemId.longValue()).orElse(null);
            if (item != null) {
                item.setCurrentPrice(avgPrice);
                itemRepository.save(item);
            } else {
                // Optionally handle missing item (e.g., log or create new)
                System.out.println("Item with ID " + itemId + " not found in DB.");
            }
        }
    }

    /**
     * Method for manually triggering the fetch process, can be called from controller
     * @return true if fetch started, false if already in progress or missing credentials
     */
    public boolean triggerFetch() {
        if (!fetchLock.tryLock()) {
            System.out.println("Fetch already in progress.");
            return false;
        }
        try {
            if (clientId == null || clientSecret == null) {
            System.out.println("Missing clientId/secret - check env vars and application.properties");
            return false;
            }
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
                // Calculate average prices
                Map<Integer, Long> avgPrices = auctionProcesser.calculateAveragePrices(matches);
                // Save to DB
                saveItemsToDb(avgPrices);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            fetchLock.unlock();
        }
    }


    @PostConstruct
    public void init() {
        clientId = blizzConfig.getClientId();
        clientSecret = blizzConfig.getClientSecret();
    }
}
