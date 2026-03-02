package com.crafting.scraper;

import java.time.OffsetDateTime;
import java.util.List;

public record ScraperResult(
        String professionSlug,
        String expansionSlug,
        int added,
        int updated,
        int skipped,
        int listingPagesVisited,
        int listingEntriesFound,
        List<String> errors,
        List<Long> autoCreatedItemIds,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
