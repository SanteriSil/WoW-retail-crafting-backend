package com.crafting.scraper;

import java.time.OffsetDateTime;
import java.util.List;

public record ScraperResult(
        String professionSlug,
        String expansionSlug,
        int listingPagesVisited,
        int listingEntriesFound,
        int recipesParsed,
        List<String> errors,
        List<WowheadPageParser.RecipeDetailData> recipes,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
