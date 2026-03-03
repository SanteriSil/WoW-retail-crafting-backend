package com.crafting.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts recipe data from Wowhead listing pages.
 * <p>
 * Wowhead renders its spell tables client-side via JavaScript, so the data
 * is <b>not</b> available in the server-rendered DOM.  Instead, the raw HTML
 * contains a JavaScript variable {@code listviewspells = [&#x2026;]} that
 * holds every recipe entry as a JSON-like object with fields such as
 * {@code id}, {@code name}, {@code creates}, {@code reagents}, etc.
 * <p>
 * This parser extracts that JS array, normalises it into valid JSON, and
 * returns fully populated {@link RecipeDetailData} records — no detail-page
 * fetch required.
 */
@Component
public class WowheadPageParser {

    private static final Logger log = LoggerFactory.getLogger(WowheadPageParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses all craftable recipes from a Wowhead profession listing page.
     *
     * @param html    the full HTML of the listing page
     * @param pageUrl the URL the page was fetched from (for error messages / source links)
     * @return a list of {@link RecipeDetailData} for every recipe that has an output item
     */
    public List<RecipeDetailData> parseRecipesFromListingPage(String html, String pageUrl) {
        String jsonArray = extractJsonArray(html, "listviewspells");
        if (jsonArray == null) {
            throw new IllegalStateException(
                    "Listing parse failed: no listviewspells data found in " + pageUrl);
        }

        // Wowhead may emit bare (unquoted) object keys — normalise to strict JSON.
        jsonArray = fixJavaScriptObjectNotation(jsonArray);

        JsonNode entries;
        try {
            entries = MAPPER.readTree(jsonArray);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Listing parse failed: could not parse listviewspells JSON in " + pageUrl, e);
        }

        String baseUrl = extractBaseUrl(pageUrl);
        List<RecipeDetailData> results = new ArrayList<>();

        for (JsonNode entry : entries) {
            // Skip non-crafting spells (no output item)
            if (!entry.has("creates") || entry.get("creates").isNull()) {
                continue;
            }

            long spellId = entry.get("id").asLong();
            String name = entry.has("name") ? entry.get("name").asText() : "Unknown Recipe";

            JsonNode creates = entry.get("creates");
            long outputItemId = creates.get(0).asLong();
            // creates = [itemId, minQty, maxQty] — use minQty as the base output quantity
            float outputQuantity = creates.size() > 1 ? creates.get(1).floatValue() : 1.0f;

            List<IngredientData> reagents = new ArrayList<>();
            if (entry.has("reagents") && entry.get("reagents").isArray()) {
                for (JsonNode reagent : entry.get("reagents")) {
                    long itemId = reagent.get(0).asLong();
                    int qty = reagent.size() > 1 ? reagent.get(1).asInt() : 1;
                    reagents.add(new IngredientData(itemId, "", qty));
                }
            }

            String detailUrl = baseUrl + "/spell=" + spellId;

            results.add(new RecipeDetailData(
                    spellId, name, outputItemId, "", outputQuantity,
                    reagents, List.of(), detailUrl));
        }

        if (results.isEmpty()) {
            throw new IllegalStateException(
                    "Listing parse failed: no craftable recipes found in " + pageUrl);
        }

        log.info("Extracted {} craftable recipes from listing page: {}", results.size(), pageUrl);
        return results;
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * Finds a top-level JSON array assigned to the named JavaScript variable
     * by bracket-counting (handles nested arrays / objects safely).
     */
    private String extractJsonArray(String html, String variableName) {
        int marker = html.indexOf(variableName);
        if (marker < 0) {
            return null;
        }

        int bracketStart = html.indexOf('[', marker);
        if (bracketStart < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = bracketStart; i < html.length(); i++) {
            char c = html.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }

            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return html.substring(bracketStart, i + 1);
                }
            }
        }

        return null; // unbalanced brackets
    }

    /**
     * Ensures every object key in a JavaScript expression is double-quoted so
     * it becomes valid JSON.  Already-quoted keys are left as-is because the
     * regex only matches bare identifiers.
     */
    private String fixJavaScriptObjectNotation(String js) {
        return js.replaceAll("(?<=[{,])\\s*([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*:", "\"$1\":");
    }

    /**
     * Extracts the scheme + host from a full URL.
     * e.g. {@code https://www.wowhead.com/spells/...} → {@code https://www.wowhead.com}
     */
    private String extractBaseUrl(String pageUrl) {
        int schemeEnd = pageUrl.indexOf("//");
        if (schemeEnd < 0) {
            return pageUrl;
        }
        int pathStart = pageUrl.indexOf('/', schemeEnd + 2);
        return pathStart > 0 ? pageUrl.substring(0, pathStart) : pageUrl;
    }

    // ---- data records -----------------------------------------------------

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
