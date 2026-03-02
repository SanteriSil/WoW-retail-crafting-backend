package com.crafting.scraper;

import com.crafting.model.Expansion;
import com.crafting.model.Profession;
import com.crafting.repository.ExpansionRepository;
import com.crafting.repository.ProfessionRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(ScraperConfig.class)
public class WowheadScraper {

    private static final Logger log = LoggerFactory.getLogger(WowheadScraper.class);

    private final ScraperConfig scraperConfig;
    private final WowheadPageParser pageParser;
    private final ProfessionRepository professionRepository;
    private final ExpansionRepository expansionRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ReentrantLock scrapeLock = new ReentrantLock();
    private final ExecutorService scraperExecutor = Executors.newSingleThreadExecutor();

    private volatile boolean running = false;
    private volatile OffsetDateTime lastStartedAt;
    private volatile OffsetDateTime lastFinishedAt;
    private volatile ScraperResult lastResult;

    public WowheadScraper(ScraperConfig scraperConfig,
                          WowheadPageParser pageParser,
                          ProfessionRepository professionRepository,
                          ExpansionRepository expansionRepository) {
        this.scraperConfig = scraperConfig;
        this.pageParser = pageParser;
        this.professionRepository = professionRepository;
        this.expansionRepository = expansionRepository;
    }

    public boolean triggerScrape(Integer professionId, Integer expansionId) {
        if (!scraperConfig.isEnabled()) {
            throw new IllegalStateException("Scraper is disabled by configuration");
        }
        if (!scrapeLock.tryLock()) {
            return false;
        }

        Profession profession = professionRepository.findById(professionId)
                .orElseThrow(() -> new IllegalArgumentException("Profession not found: " + professionId));
        Expansion expansion = expansionRepository.findById(expansionId)
                .orElseThrow(() -> new IllegalArgumentException("Expansion not found: " + expansionId));

        running = true;
        lastStartedAt = OffsetDateTime.now();

        scraperExecutor.submit(() -> {
            try {
                lastResult = runScrape(profession, expansion);
            } catch (Exception e) {
                log.error("Wowhead scrape failed for profession {} and expansion {}", professionId, expansionId, e);
                lastResult = new ScraperResult(
                        slugify(profession.getName()),
                        expansion.getSlug(),
                        0,
                        0,
                        0,
                        List.of(e.getMessage()),
                        List.of(),
                        lastStartedAt,
                        OffsetDateTime.now()
                );
            } finally {
                running = false;
                lastFinishedAt = OffsetDateTime.now();
                scrapeLock.unlock();
            }
        });

        return true;
    }

    public ScraperStatus getStatus() {
        return new ScraperStatus(running, lastStartedAt, lastFinishedAt, lastResult);
    }

    private ScraperResult runScrape(Profession profession, Expansion expansion) {
        OffsetDateTime startedAt = OffsetDateTime.now();

        String professionSlug = slugify(profession.getName());
        String expansionSlug = expansion.getSlug();
        String listingUrl = buildListingUrl(professionSlug, expansionSlug);

        List<String> errors = new ArrayList<>();

        int pagesVisited = 0;
        List<WowheadPageParser.ListingRecipeData> listingEntries = new ArrayList<>();
        Set<String> visitedUrls = new java.util.HashSet<>();

        String nextUrl = listingUrl;
        while (nextUrl != null && !visitedUrls.contains(nextUrl)) {
            visitedUrls.add(nextUrl);
            pagesVisited++;
            String html = fetchHtml(nextUrl);
            listingEntries.addAll(pageParser.parseListingPage(html, nextUrl));
            nextUrl = pageParser.parseNextPageUrl(html, nextUrl).orElse(null);
            sleepRequestDelay();
        }

        Map<Long, WowheadPageParser.ListingRecipeData> uniqueBySpellId = new LinkedHashMap<>();
        for (WowheadPageParser.ListingRecipeData entry : listingEntries) {
            uniqueBySpellId.putIfAbsent(entry.spellId(), entry);
        }

        List<WowheadPageParser.RecipeDetailData> parsedRecipes = new ArrayList<>();
        for (WowheadPageParser.ListingRecipeData listing : uniqueBySpellId.values()) {
            try {
                String detailHtml = fetchHtml(listing.detailUrl());
                WowheadPageParser.RecipeDetailData detail = pageParser.parseRecipeDetailPage(
                        detailHtml,
                        listing.detailUrl(),
                        listing.spellId()
                );
                parsedRecipes.add(detail);
            } catch (Exception e) {
                errors.add("Spell " + listing.spellId() + ": " + e.getMessage());
            }
            sleepRequestDelay();
        }

        return new ScraperResult(
                professionSlug,
                expansionSlug,
                pagesVisited,
                uniqueBySpellId.size(),
                parsedRecipes.size(),
                List.copyOf(errors),
                List.copyOf(parsedRecipes),
                startedAt,
                OffsetDateTime.now()
        );
    }

    private String buildListingUrl(String professionSlug, String expansionSlug) {
        String suffix = resolveProfessionSuffix(professionSlug);
        return String.format(
                "%s/spells/professions/%s/%s-%s",
                stripTrailingSlash(scraperConfig.getBaseUrl()),
                professionSlug,
                expansionSlug,
                suffix
        );
    }

    private String resolveProfessionSuffix(String professionSlug) {
        return switch (professionSlug) {
            case "alchemy" -> "recipes";
            case "blacksmithing" -> "plans";
            case "enchanting" -> "formulas";
            case "engineering" -> "schematics";
            case "inscription" -> "techniques";
            case "jewelcrafting" -> "designs";
            case "leatherworking", "tailoring" -> "patterns";
            default -> throw new IllegalArgumentException("Unsupported profession slug for Wowhead listing: " + professionSlug);
        };
    }

    private String fetchHtml(String url) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", scraperConfig.getUserAgent())
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 429 && attempt <= 4) {
                    long backoff = scraperConfig.getRequestDelayMs() * (1L << (attempt - 1));
                    log.warn("Wowhead returned 429 for {}. Backing off {} ms (attempt {})", url, backoff, attempt);
                    sleep(backoff);
                    continue;
                }

                if (status >= 400) {
                    throw new IOException("HTTP " + status + " for URL " + url);
                }

                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while fetching URL: " + url, e);
            } catch (IOException e) {
                if (attempt >= 3) {
                    throw new RuntimeException("Failed to fetch URL after retries: " + url, e);
                }
                sleep(scraperConfig.getRequestDelayMs());
            }
        }
    }

    private void sleepRequestDelay() {
        sleep(scraperConfig.getRequestDelayMs());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during scraper delay", e);
        }
    }

    private String slugify(String name) {
        return name == null ? "" : name.trim().toLowerCase().replace(" ", "-");
    }

    private String stripTrailingSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://www.wowhead.com";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    @PreDestroy
    public void shutdownExecutor() {
        scraperExecutor.shutdownNow();
    }

    public record ScraperStatus(
            boolean running,
            OffsetDateTime lastStartedAt,
            OffsetDateTime lastFinishedAt,
            ScraperResult lastResult
    ) {
    }
}
