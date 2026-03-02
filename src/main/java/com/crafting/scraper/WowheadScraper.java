package com.crafting.scraper;

import com.crafting.model.Expansion;
import com.crafting.model.Item;
import com.crafting.model.Profession;
import com.crafting.model.Recipe;
import com.crafting.model.RecipeIngredient;
import com.crafting.model.RecipeOptionalIngredient;
import com.crafting.model.RecipeOptionalIngredientGroup;
import com.crafting.repository.ExpansionRepository;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.ProfessionRepository;
import com.crafting.repository.RecipeRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ItemRepository itemRepository;
    private final RecipeRepository recipeRepository;

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
                          ExpansionRepository expansionRepository,
                          ItemRepository itemRepository,
                          RecipeRepository recipeRepository) {
        this.scraperConfig = scraperConfig;
        this.pageParser = pageParser;
        this.professionRepository = professionRepository;
        this.expansionRepository = expansionRepository;
        this.itemRepository = itemRepository;
        this.recipeRepository = recipeRepository;
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

    public boolean triggerScrape(String professionSlug, String expansionSlug) {
        Profession profession = findProfessionBySlug(professionSlug)
                .orElseThrow(() -> new IllegalArgumentException("Profession not found for slug: " + professionSlug));
        Expansion expansion = expansionRepository.findBySlug(expansionSlug)
                .orElseThrow(() -> new IllegalArgumentException("Expansion not found for slug: " + expansionSlug));
        return triggerScrape(profession.getId(), expansion.getId());
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
        List<Long> autoCreatedItemIds = new ArrayList<>();
        int added = 0;
        int updated = 0;
        int skipped = 0;

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

        for (WowheadPageParser.ListingRecipeData listing : uniqueBySpellId.values()) {
            try {
                String detailHtml = fetchHtml(listing.detailUrl());
                WowheadPageParser.RecipeDetailData detail = pageParser.parseRecipeDetailPage(
                        detailHtml,
                        listing.detailUrl(),
                        listing.spellId()
                );
                UpsertOutcome outcome = upsertScrapedRecipe(detail, profession, expansion, autoCreatedItemIds);
                if (outcome == UpsertOutcome.ADDED) {
                    added++;
                } else if (outcome == UpsertOutcome.UPDATED) {
                    updated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                errors.add("Spell " + listing.spellId() + ": " + e.getMessage());
            }
            sleepRequestDelay();
        }

        return new ScraperResult(
                professionSlug,
                expansionSlug,
                added,
                updated,
                skipped,
                pagesVisited,
                uniqueBySpellId.size(),
                List.copyOf(errors),
                List.copyOf(autoCreatedItemIds),
                startedAt,
                OffsetDateTime.now()
        );
    }

    private UpsertOutcome upsertScrapedRecipe(WowheadPageParser.RecipeDetailData detail,
                                              Profession profession,
                                              Expansion expansion,
                                              List<Long> autoCreatedItemIds) {
        Item outputItem = ensureItem(detail.outputItemId(), detail.outputItemName(), autoCreatedItemIds);

        List<RecipeIngredientSpec> requiredSpecs = detail.requiredIngredients().stream()
                .map(i -> new RecipeIngredientSpec(
                        ensureItem(i.itemId(), i.itemName(), autoCreatedItemIds),
                        i.quantity()))
                .toList();

        List<OptionalGroupSpec> optionalSpecs = detail.optionalIngredientGroups().stream()
                .map(g -> new OptionalGroupSpec(
                        g.slotIndex(),
                        g.label(),
                        g.options().stream()
                                .map(o -> new RecipeIngredientSpec(
                                        ensureItem(o.itemId(), o.itemName(), autoCreatedItemIds),
                                        o.quantity()))
                                .toList()))
                .toList();

        Recipe existing = recipeRepository.findByWowheadSpellId(detail.spellId()).orElse(null);
        if (existing == null) {
            Recipe created = new Recipe();
            applyRecipeState(created, detail, outputItem, profession, expansion, requiredSpecs, optionalSpecs);
            recipeRepository.save(created);
            log.info("Scraper added recipe spellId={} name={}", detail.spellId(), detail.recipeName());
            return UpsertOutcome.ADDED;
        }

        if (isUnchanged(existing, detail, outputItem, profession, expansion, requiredSpecs, optionalSpecs)) {
            log.debug("Scraper skipped unchanged recipe spellId={} name={}", detail.spellId(), detail.recipeName());
            return UpsertOutcome.SKIPPED;
        }

        applyRecipeState(existing, detail, outputItem, profession, expansion, requiredSpecs, optionalSpecs);
        recipeRepository.save(existing);
        log.info("Scraper updated recipe spellId={} name={}", detail.spellId(), detail.recipeName());
        return UpsertOutcome.UPDATED;
    }

    private void applyRecipeState(Recipe recipe,
                                  WowheadPageParser.RecipeDetailData detail,
                                  Item outputItem,
                                  Profession profession,
                                  Expansion expansion,
                                  List<RecipeIngredientSpec> requiredSpecs,
                                  List<OptionalGroupSpec> optionalSpecs) {
        recipe.setName(detail.recipeName());
        recipe.setWowheadSpellId(detail.spellId());
        recipe.setOutputItem(outputItem);
        recipe.setOutputQuantity(detail.outputQuantity());
        recipe.setProfession(profession);
        recipe.setExpansion(expansion);
        recipe.setSource("SCRAPED");
        recipe.setDeleted(false);
        recipe.setCreatedBy(null);

        recipe.getIngredients().clear();
        for (RecipeIngredientSpec spec : requiredSpecs) {
            RecipeIngredient ingredient = RecipeIngredient.builder()
                    .recipe(recipe)
                    .item(spec.item())
                    .quantity(spec.quantity())
                    .build();
            recipe.getIngredients().add(ingredient);
        }

        recipe.getOptionalIngredientGroups().clear();
        for (OptionalGroupSpec groupSpec : optionalSpecs) {
            RecipeOptionalIngredientGroup group = RecipeOptionalIngredientGroup.builder()
                    .recipe(recipe)
                    .slotIndex(groupSpec.slotIndex())
                    .label(groupSpec.label())
                    .build();

            for (RecipeIngredientSpec optionSpec : groupSpec.options()) {
                RecipeOptionalIngredient option = RecipeOptionalIngredient.builder()
                        .group(group)
                        .item(optionSpec.item())
                        .quantity(optionSpec.quantity())
                        .build();
                group.getOptions().add(option);
            }

            recipe.getOptionalIngredientGroups().add(group);
        }
    }

    private boolean isUnchanged(Recipe recipe,
                                WowheadPageParser.RecipeDetailData detail,
                                Item outputItem,
                                Profession profession,
                                Expansion expansion,
                                List<RecipeIngredientSpec> requiredSpecs,
                                List<OptionalGroupSpec> optionalSpecs) {
        if (!safeEquals(recipe.getName(), detail.recipeName())) {
            return false;
        }
        if (!safeEquals(recipe.getOutputItem().getId(), outputItem.getId())) {
            return false;
        }
        if (recipe.getOutputQuantity() == null || Float.compare(recipe.getOutputQuantity(), detail.outputQuantity()) != 0) {
            return false;
        }
        if (recipe.getProfession() == null || !safeEquals(recipe.getProfession().getId(), profession.getId())) {
            return false;
        }
        if (recipe.getExpansion() == null || !safeEquals(recipe.getExpansion().getId(), expansion.getId())) {
            return false;
        }

        List<String> existingRequired = recipe.getIngredients().stream()
                .sorted(Comparator.comparing((RecipeIngredient i) -> i.getItem().getId()))
                .map(i -> i.getItem().getId() + ":" + i.getQuantity())
                .toList();
        List<String> incomingRequired = requiredSpecs.stream()
                .sorted(Comparator.comparing((RecipeIngredientSpec s) -> s.item().getId()))
                .map(i -> i.item().getId() + ":" + i.quantity())
                .toList();
        if (!existingRequired.equals(incomingRequired)) {
            return false;
        }

        List<String> existingOptional = recipe.getOptionalIngredientGroups().stream()
                .sorted(Comparator.comparing(RecipeOptionalIngredientGroup::getSlotIndex))
                .flatMap(g -> g.getOptions().stream()
                        .sorted(Comparator.comparing((RecipeOptionalIngredient o) -> o.getItem().getId()))
                        .map(o -> g.getSlotIndex() + "|" + safeLabel(g.getLabel()) + "|" + o.getItem().getId() + ":" + o.getQuantity()))
                .toList();
        List<String> incomingOptional = optionalSpecs.stream()
                .sorted(Comparator.comparing(OptionalGroupSpec::slotIndex))
                .flatMap(g -> g.options().stream()
                        .sorted(Comparator.comparing((RecipeIngredientSpec o) -> o.item().getId()))
                        .map(o -> g.slotIndex() + "|" + safeLabel(g.label()) + "|" + o.item().getId() + ":" + o.quantity()))
                .toList();
        return existingOptional.equals(incomingOptional);
    }

    private Item ensureItem(long itemId, String itemName, List<Long> autoCreatedItemIds) {
        return itemRepository.findById(itemId)
                .orElseGet(() -> {
                    Item stub = Item.builder()
                            .id(itemId)
                            .name((itemName == null || itemName.isBlank()) ? ("Unknown Item " + itemId) : itemName.trim())
                            .build();
                    Item saved = itemRepository.save(stub);
                    autoCreatedItemIds.add(saved.getId());
                    log.info("Auto-created item stub from scraper: {} ({})", saved.getName(), saved.getId());
                    return saved;
                });
    }

    private Optional<Profession> findProfessionBySlug(String professionSlug) {
        String normalized = professionSlug == null ? "" : professionSlug.trim().toLowerCase();
        return professionRepository.findAll().stream()
                .filter(p -> slugify(p.getName()).equals(normalized))
                .findFirst();
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private String safeLabel(String label) {
        return label == null ? "" : label;
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

    private enum UpsertOutcome {
        ADDED,
        UPDATED,
        SKIPPED
    }

    private record RecipeIngredientSpec(Item item, int quantity) {
    }

    private record OptionalGroupSpec(short slotIndex, String label, List<RecipeIngredientSpec> options) {
    }
}
