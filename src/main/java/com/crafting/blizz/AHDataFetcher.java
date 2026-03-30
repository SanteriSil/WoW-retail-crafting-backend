package com.crafting.blizz;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;

import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.crafting.repository.ItemRepository;
import com.crafting.cache.CachedResult;
import com.crafting.model.Item;


@Service
@EnableConfigurationProperties(BlizzConfig.class)
public class AHDataFetcher {
    /**
     * Items whose price was updated within this window (e.g. by addon chunk submissions)
     * are skipped during scheduled Blizzard API fetches so that fresher addon data is
     * not overwritten.
     */
    private static final Duration ADDON_FRESHNESS_THRESHOLD = Duration.ofHours(1);

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
     * Scheduled method to fetch AH data at :00 and :30 of every hour.
     * Items whose price was recently updated (within {@link #ADDON_FRESHNESS_THRESHOLD})
     * are skipped so that fresher addon data is not overwritten.
     */
    @Scheduled(cron = "0 0,30 * * * *")
    public void callApi() {
        logger.info("Scheduled task triggered: Fetching AH data");
        try {
            triggerFetch();
        } catch (Exception e) {
            logger.error("Error during scheduled fetch", e);
        }
    }

    /**
     * Persists computed average prices to the database.
     *
     * @param avgPrices       map of item-id → (avgPrice, quantity)
     * @param skipRecentlyUpdated when {@code true}, items whose price was updated within
     *                            {@link #ADDON_FRESHNESS_THRESHOLD} are left untouched so that
     *                            fresher addon data is not overwritten by Blizzard API results
     */
    @Transactional
    private int saveItemsToDb(Map<Integer, Pair<Long, Long>> avgPrices, boolean skipRecentlyUpdated) {
        OffsetDateTime freshnessCutoff = skipRecentlyUpdated
                ? OffsetDateTime.now().minus(ADDON_FRESHNESS_THRESHOLD)
                : null;
        int updated = 0;
        int skipped = 0;

        for (Map.Entry<Integer, Pair<Long, Long>> entry : avgPrices.entrySet()) {
            Integer itemId = entry.getKey();
            Long avgPrice = entry.getValue().getLeft();
            Long quantity = entry.getValue().getRight();
            Item item = itemRepository.findById(itemId.longValue()).orElse(null);
            if (item == null) {
                logger.warn("Item with ID {} not found in DB.", itemId);
                continue;
            }

            if (freshnessCutoff != null
                    && item.getCurrentPriceRecordedAt() != null
                    && item.getCurrentPriceRecordedAt().isAfter(freshnessCutoff)) {
                skipped++;
                continue;
            }

            item.setCurrentPrice(avgPrice);
            item.setQuantity(quantity);
            item.setCurrentPriceRecordedAt(OffsetDateTime.now());
            itemRepository.save(item);
            updated++;
        }

        if (skipped > 0) {
            logger.info("Saved {} item prices, skipped {} recently-updated items", updated, skipped);
        } else {
            logger.info("Saved {} item prices", updated);
        }
        itemCache.invalidate();
        return updated;
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
    public void processAuctionData(InputStream inputStream) throws IOException {
        logger.debug("Processing auction data");
        Map<Integer, List<AuctionEntry>> matches = auctionProcesser.processAndCollectStreaming(
            inputStream,
            fetchDbItemIds()
        );
        logger.debug("Calculating average prices for matched items");
        Map<Integer, Pair<Long, Long>> avgPrices = auctionProcesser.calculateAveragePrices(matches);
        logger.debug("Saving average prices to database");
        saveItemsToDb(avgPrices, true);
    }

    @Transactional
    public int processAuctionDataForItems(InputStream inputStream, Set<Integer> targetItemIds) throws IOException {
        logger.debug("Processing auction data for {} targeted items", targetItemIds.size());
        Set<Integer> dbItemIds = fetchDbItemIds();
        Set<Integer> filteredTargetIds = new HashSet<>(targetItemIds);
        filteredTargetIds.retainAll(dbItemIds);

        if (filteredTargetIds.isEmpty()) {
            return 0;
        }

        Map<Integer, Pair<Long, Long>> avgPrices = auctionProcesser.processForItems(inputStream, filteredTargetIds);
        logger.debug("Saving targeted average prices to database");
        return saveItemsToDb(avgPrices, false);
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
        saveItemsToDb(avgPrices, false);
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
        SubmissionResult result = submitAuctionDataDetailed(csv);
        return result.updatedCount();
    }

    public SubmissionResult submitAuctionDataDetailed(String csv) {
        if (!fetchLock.tryLock()) {
            logger.warn("Fetch already in progress, skipping user-submitted data");
            return new SubmissionResult(-1, Collections.emptyMap());
        }
        try {
            Map<Integer, Pair<Long, Long>> results = processCsvAuctionData(csv);
            logger.info("User-submitted auction data processed – {} item prices updated", results.size());
            return new SubmissionResult(results.size(), results);
        } catch (Exception e) {
            logger.error("Error processing user-submitted auction data", e);
            return new SubmissionResult(-1, Collections.emptyMap());
        } finally {
            fetchLock.unlock();
        }
    }

    public record SubmissionResult(int updatedCount, Map<Integer, Pair<Long, Long>> submissions) {}

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

            // Stream the response instead of loading entire JSON into a String
            // to avoid OOM on large auction datasets (expansion launches etc.)
            HttpStatusCode status = blizzApiClient.streamCommodities(accessToken, inputStream -> {
                processAuctionData(inputStream);
            });
            logger.debug("API response status: {}", status);

            logger.debug("Fetching missing item icons from Blizzard media API");
            populateMissingIcons(accessToken);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            fetchLock.unlock();
        }
    }

    public int triggerFetchForItems(Set<Integer> itemIds) throws IOException {
        Set<Integer> targetItemIds = itemIds == null ? Collections.emptySet() : new HashSet<>(itemIds);
        if (targetItemIds.isEmpty()) {
            return 0;
        }

        if (!fetchLock.tryLock()) {
            logger.warn("Fetch already in progress, skipping targeted trigger");
            return -1;
        }

        try {
            if (clientId == null || clientSecret == null) {
                logger.warn("Missing clientId/secret - check env vars and application.properties");
                return 0;
            }

            String accessToken = tokenService.getAccessToken(clientId, clientSecret);
            final int[] updatedCount = {0};

            HttpStatusCode status = blizzApiClient.streamCommodities(accessToken, inputStream -> {
                updatedCount[0] = processAuctionDataForItems(inputStream, targetItemIds);
            });
            logger.debug("Targeted API response status: {}", status);
            return updatedCount[0];
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
