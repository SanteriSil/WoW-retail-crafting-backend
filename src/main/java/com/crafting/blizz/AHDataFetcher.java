package com.crafting.blizz;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;

import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import com.crafting.repository.ItemRepository;
import com.crafting.cache.CachedResult;
import com.crafting.model.Item;


@Service
@EnableConfigurationProperties(BlizzConfig.class)
public class AHDataFetcher {
    private static final Duration MANUAL_SUBMIT_SCHEDULE_PAUSE = Duration.ofMinutes(30);

    private final BlizzConfig blizzConfig;
    private final TokenService tokenService;
    private final BlizzApiClient blizzApiClient;
    private final AuctionProcesser auctionProcesser;
    private String clientId;
    private String clientSecret;
    private final ItemRepository itemRepository;
    private final CachedResult<List<Item>> itemCache;
    private final CachedResult<List<Long>> itemIdCache;
    private final ReentrantLock fetchLock = new ReentrantLock();
    private volatile Instant scheduledFetchPausedUntil = Instant.EPOCH;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AHDataFetcher.class);


    public AHDataFetcher(BlizzConfig blizzConfig, TokenService tokenService,
                        BlizzApiClient blizzApiClient, AuctionProcesser auctionProcesser,
                        ItemRepository itemRepository,
                        CachedResult<List<Item>> itemCache,
                        CachedResult<List<Long>> itemIdCache) {
        this.blizzConfig = blizzConfig;
        this.tokenService = tokenService;
        this.blizzApiClient = blizzApiClient;
        this.auctionProcesser = auctionProcesser;
        this.itemRepository = itemRepository;
        this.itemCache = itemCache;
        this.itemIdCache = itemIdCache;
    }

    //gets item IDs from repo
    //set unnecesary since IDs should be unique
    //change later
    private HashSet<Integer> fetchDbItemIds() {
        logger.debug("Fetching item IDs from database");
        List<Item> items = itemRepository.findAll();
        HashSet<Integer> itemIds = new HashSet<>();
        for (Item item : items) {
            itemIds.add(item.getId().intValue());
        }
        logger.debug("Fetched {} item IDs from database", itemIds.size());
        return itemIds;
    }

    /**
     * Scheduled method to fetch AH data every 20 minutes. Checks if a manual submit has recently paused the schedule to avoid conflicts.
     */
    @Scheduled(cron = "0 */20 * * * *")
    public void callApi() {
        if (isScheduledFetchPaused()) {
            long remainingMinutes = Math.max(1, Duration.between(Instant.now(), scheduledFetchPausedUntil).toMinutes());
            logger.info("Scheduled fetch skipped: paused for manual submit ({} minute(s) remaining)", remainingMinutes);
            return;
        }

        logger.info("Scheduled task triggered: Fetching AH data");
        try {
            triggerFetch();
        } catch (Exception e) {
            logger.error("Error during scheduled fetch", e);
        }
    }

    private boolean isScheduledFetchPaused() {
        return Instant.now().isBefore(scheduledFetchPausedUntil);
    }

    private void pauseScheduledFetchForManualSubmit() {
        Instant pauseUntil = Instant.now().plus(MANUAL_SUBMIT_SCHEDULE_PAUSE);
        if (pauseUntil.isAfter(scheduledFetchPausedUntil)) {
            scheduledFetchPausedUntil = pauseUntil;
        }
        logger.info("Scheduled AH fetch paused until {} due to manual auction submission", scheduledFetchPausedUntil);
    }

    @Transactional
    private void saveItemsToDb(Map<Integer, Pair<Long, Long>> avgPrices) {
        for (Map.Entry<Integer, Pair<Long, Long>> entry : avgPrices.entrySet()) {
            Integer itemId = entry.getKey();
            Long avgPrice = entry.getValue().getLeft(); // Get the average price from the Pair
            Long quantity = entry.getValue().getRight(); // Get the quantity from the Pair
            Item item = itemRepository.findById(itemId.longValue()).orElse(null);
            if (item != null) {
                logger.info("Updating item ID {} with new average price {} and quantity {}", itemId, avgPrice, quantity);
                item.setCurrentPrice(avgPrice);
                item.setQuantity(quantity);
                item.setCurrentPriceRecordedAt(OffsetDateTime.now());
                itemRepository.save(item);
            } else {
                // Optionally handle missing item (e.g., log or create new)
                logger.warn("Item with ID {} not found in DB.", itemId);
            }
        }
        itemCache.invalidate();
    }

    @Transactional
    private void populateMissingIcons(String accessToken) {
        List<Item> missingIconItems = itemRepository.findByIconUrlIsNullOrIconUrl("");
        if (missingIconItems.isEmpty()) {
            logger.debug("No items missing icon URL");
            return;
        }

        logger.debug("Fetching icons for {} items missing icon URL", missingIconItems.size());
        int updated = 0;

        for (Item item : missingIconItems) {
            try {
                blizzApiClient.fetchItemIconUrl(item.getId(), accessToken).ifPresent(iconUrl -> {
                    item.setIconUrl(iconUrl);
                    itemRepository.save(item);
                });
                if (item.getIconUrl() != null && !item.getIconUrl().isBlank()) {
                    updated++;
                }
            } catch (Exception e) {
                logger.warn("Failed to fetch icon for item ID {}", item.getId(), e);
            }
        }

        logger.debug("Updated icon URL for {} items", updated);
        if (updated > 0) {
            itemCache.invalidate();
        }
    }

    /**
     * Parses auctions from input and calculates average prices for items that are tracked in the database.
     * @param body raw JSON string containing an "auctions" array
     * @return map of item-id → computed average price, empty map on failure
     */
    @Transactional
    public void processAuctionData(String body) throws IOException {
        logger.debug("Processing auction data");
        Map<Integer, List<AuctionEntry>> matches = auctionProcesser.processAndCollect(
            body,
            fetchDbItemIds()
        );
        logger.debug("Calculating average prices for matched items");
        Map<Integer, Pair<Long, Long>> avgPrices = auctionProcesser.calculateAveragePrices(matches);
        logger.debug("Saving average prices to database");
        saveItemsToDb(avgPrices);
    }

    /**
     * Processes manually gathered auction data submitted by users
     * @param csv raw CSV text — one line per listing: itemId,unitPrice,quantity
     * @return map of item-id → computed average price
     */
    @Transactional
    public Map<Integer, Pair<Long, Long>> processCsvAuctionData(String csv) {
        logger.debug("Processing user-submitted CSV auction data");
        Map<Integer, List<AuctionEntry>> matches = auctionProcesser.parseCsvAuctions(
            csv,
            fetchDbItemIds()
        );
        logger.debug("Calculating average prices for {} matched items", matches.size());
        Map<Integer, Pair<Long, Long>> avgPrices = auctionProcesser.calculateAveragePrices(matches);
        logger.debug("Saving average prices to database");
        saveItemsToDb(avgPrices);
        return avgPrices;
    }

    /**
     * Public entry point for user-submitted CSV auction data (e.g. from an
     * in-game addon via copy-paste). Acquires the fetch lock so that
     * simultaneous Blizzard API fetches and user submissions don't collide.
     *
     * @param csv raw CSV text — one line per listing: itemId,unitPrice,quantity
     * @return number of item prices updated, or -1 if another fetch is already running
     */
    public int submitAuctionData(String csv) {
        if (!fetchLock.tryLock()) {
            logger.warn("Fetch already in progress, skipping user-submitted data");
            return -1;
        }
        try {
            pauseScheduledFetchForManualSubmit();
            Map<Integer, Pair<Long, Long>> results = processCsvAuctionData(csv);
            logger.info("User-submitted auction data processed – {} item prices updated", results.size());
            return results.size();
        } catch (Exception e) {
            logger.error("Error processing user-submitted auction data", e);
            return -1;
        } finally {
            fetchLock.unlock();
        }
    }

    /**
     * Method for manually triggering the fetch process, can be called from controller
     * @return true if fetch started, false if already in progress or missing credentials
     */
    public boolean triggerFetch() {
        if (!fetchLock.tryLock()) {
            logger.warn("Fetch already in progress, skipping new trigger");
            return false;
        }
        try {
            if (clientId == null || clientSecret == null) {
            logger.warn("Missing clientId/secret - check env vars and application.properties");
            return false;
            }
            String accessToken = tokenService.getAccessToken(clientId, clientSecret);
            ResponseEntity<String> resp = blizzApiClient.fetchCommodities(accessToken);
            logger.debug("API response status: " + resp.getStatusCode());
            String body = resp.getBody();
            if (body != null) {
                processAuctionData(body);

                logger.debug("Fetching missing item icons from Blizzard media API");
                populateMissingIcons(accessToken);
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
