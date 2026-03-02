package com.crafting.scraper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class WowheadPageParser {

    private static final Pattern SPELL_ID_PATTERN = Pattern.compile("/spell=(\\d+)");
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("/item=(\\d+)");
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(?:x|×)\\s*(\\d+)");

    public List<ListingRecipeData> parseListingPage(String html, String pageUrl) {
        Document document = Jsoup.parse(html, pageUrl);
        Elements links = document.select("table a[href*=/spell=], .listview a[href*=/spell=], a[href*=/spell=]");

        Map<Long, ListingRecipeData> uniqueBySpellId = new LinkedHashMap<>();
        for (Element link : links) {
            Long spellId = extractSpellId(link.attr("href"));
            if (spellId == null) {
                continue;
            }
            String name = link.text() == null ? "" : link.text().trim();
            if (name.isBlank()) {
                continue;
            }
            String detailUrl = normalizeUrl(link);
            uniqueBySpellId.putIfAbsent(spellId, new ListingRecipeData(spellId, name, detailUrl));
        }

        if (uniqueBySpellId.isEmpty()) {
            throw new IllegalStateException("Listing parse failed: no spell links found in " + pageUrl);
        }

        return List.copyOf(uniqueBySpellId.values());
    }

    public Optional<String> parseNextPageUrl(String html, String pageUrl) {
        Document document = Jsoup.parse(html, pageUrl);

        Element relNext = document.selectFirst("a[rel=next]");
        if (relNext != null) {
            return Optional.of(normalizeUrl(relNext));
        }

        Element labelNext = document.selectFirst("a[aria-label*=Next], a:matchesOwn((?i)^next$)");
        if (labelNext != null) {
            return Optional.of(normalizeUrl(labelNext));
        }

        return Optional.empty();
    }

    public RecipeDetailData parseRecipeDetailPage(String html, String pageUrl, long spellId) {
        Document document = Jsoup.parse(html, pageUrl);

        String recipeName = extractRecipeName(document);
        Element outputLink = findOutputItemLink(document)
                .orElseThrow(() -> new IllegalStateException(
                        "Detail parse failed: output item missing for spell " + spellId + " in " + pageUrl));

        Long outputItemId = extractItemId(outputLink.attr("href"));
        if (outputItemId == null) {
            throw new IllegalStateException("Detail parse failed: output item ID missing for spell " + spellId + " in " + pageUrl);
        }

        String outputItemName = outputLink.text() != null ? outputLink.text().trim() : "";
        float outputQuantity = 1.0f;

        List<IngredientData> ingredients = extractRequiredIngredients(document);
        List<OptionalIngredientGroupData> optionalGroups = extractOptionalIngredientGroups(document);

        return new RecipeDetailData(
                spellId,
                recipeName,
                outputItemId,
                outputItemName,
                outputQuantity,
                ingredients,
                optionalGroups,
                pageUrl
        );
    }

    private String extractRecipeName(Document document) {
        Element h1 = document.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().trim();
        }

        String title = document.title();
        if (title == null || title.isBlank()) {
            throw new IllegalStateException("Detail parse failed: recipe title missing");
        }

        int separator = title.indexOf(" - ");
        if (separator > 0) {
            return title.substring(0, separator).trim();
        }
        return title.trim();
    }

    private Optional<Element> findOutputItemLink(Document document) {
        Element specific = document.selectFirst("#spelldetails a[href*=/item=], #tab-created-by-spell a[href*=/item=]");
        if (specific != null) {
            return Optional.of(specific);
        }

        Element fallback = document.selectFirst("a[href*=/item=]");
        return Optional.ofNullable(fallback);
    }

    private List<IngredientData> extractRequiredIngredients(Document document) {
        Elements links = document.select("#reagents a[href*=/item=], .reagents a[href*=/item=], .spell-reagents a[href*=/item=], .listview-mode-default a[href*=/item=]");

        Map<Long, IngredientData> unique = new LinkedHashMap<>();
        for (Element link : links) {
            Long itemId = extractItemId(link.attr("href"));
            if (itemId == null) {
                continue;
            }

            String name = link.text() == null ? "" : link.text().trim();
            int quantity = inferQuantity(link);
            unique.putIfAbsent(itemId, new IngredientData(itemId, name, quantity));
        }

        return new ArrayList<>(unique.values());
    }

    private List<OptionalIngredientGroupData> extractOptionalIngredientGroups(Document document) {
        Elements groupContainers = document.select(
                "section:has(h2:matchesOwn((?i)optional)), "
                + "div:has(> h3:matchesOwn((?i)optional)), "
                + "div:has(> h4:matchesOwn((?i)optional))"
        );

        List<OptionalIngredientGroupData> groups = new ArrayList<>();
        short slotIndex = 0;
        for (Element container : groupContainers) {
            String label = container.select("h2, h3, h4").stream()
                    .map(Element::text)
                    .filter(text -> text != null && !text.isBlank())
                    .findFirst()
                    .orElse("Optional Reagent");

            Map<Long, IngredientData> options = new LinkedHashMap<>();
            for (Element link : container.select("a[href*=/item=]")) {
                Long itemId = extractItemId(link.attr("href"));
                if (itemId == null) {
                    continue;
                }
                String name = link.text() == null ? "" : link.text().trim();
                int quantity = inferQuantity(link);
                options.putIfAbsent(itemId, new IngredientData(itemId, name, quantity));
            }

            if (!options.isEmpty()) {
                groups.add(new OptionalIngredientGroupData(slotIndex++, label, new ArrayList<>(options.values())));
            }
        }

        return groups;
    }

    private int inferQuantity(Element link) {
        Element row = link.closest("tr, li, div");
        String source = row != null ? row.text() : link.text();

        Matcher matcher = QUANTITY_PATTERN.matcher(source);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1;
    }

    private String normalizeUrl(Element link) {
        String abs = link.absUrl("href");
        if (abs != null && !abs.isBlank()) {
            return abs;
        }
        return link.attr("href");
    }

    private Long extractSpellId(String href) {
        Matcher matcher = SPELL_ID_PATTERN.matcher(href);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    private Long extractItemId(String href) {
        Matcher matcher = ITEM_ID_PATTERN.matcher(href);
        if (!matcher.find()) {
            return null;
        }
        return Long.parseLong(matcher.group(1));
    }

    public record ListingRecipeData(
            long spellId,
            String recipeName,
            String detailUrl
    ) {
    }

    public record IngredientData(
            long itemId,
            String itemName,
            int quantity
    ) {
    }

    public record OptionalIngredientGroupData(
            short slotIndex,
            String label,
            List<IngredientData> options
    ) {
    }

    public record RecipeDetailData(
            long spellId,
            String recipeName,
            long outputItemId,
            String outputItemName,
            float outputQuantity,
            List<IngredientData> requiredIngredients,
            List<OptionalIngredientGroupData> optionalIngredientGroups,
            String sourceUrl
    ) {
    }
}
