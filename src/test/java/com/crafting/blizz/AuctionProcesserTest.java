package com.crafting.blizz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuctionProcesser}.
 * No Spring context needed — instantiated directly.
 */
class AuctionProcesserTest {

    private AuctionProcesser processer;

    @BeforeEach
    void setUp() {
        processer = new AuctionProcesser();
    }

    // ── CSV Parsing ────────────────────────────────────────────────────

    @Nested
    @DisplayName("parseCsvAuctions")
    class ParseCsvAuctions {

        @Test
        @DisplayName("parses valid CSV lines correctly")
        void validData() {
            String csv = "100,50000,10\n200,30000,5\n";
            Set<Integer> dbIds = Set.of(100, 200, 300);

            var result = processer.parseCsvAuctions(csv, dbIds);

            assertThat(result).hasSize(2);
            assertThat(result.get(100)).hasSize(1);
            assertThat(result.get(100).get(0).getUnitPrice()).isEqualTo(50000);
            assertThat(result.get(100).get(0).getQuantity()).isEqualTo(10);
            assertThat(result.get(200)).hasSize(1);
            assertThat(result.get(200).get(0).getUnitPrice()).isEqualTo(30000);
        }

        @Test
        @DisplayName("returns empty map for null input")
        void nullInput() {
            var result = processer.parseCsvAuctions(null, Set.of(100));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty map for blank input")
        void blankInput() {
            var result = processer.parseCsvAuctions("   \n  \n", Set.of(100));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("filters entries to only known DB item IDs")
        void filtersToDbIds() {
            String csv = "100,50000,10\n999,30000,5\n";
            Set<Integer> dbIds = Set.of(100); // 999 NOT in DB

            var result = processer.parseCsvAuctions(csv, dbIds);

            assertThat(result).hasSize(1);
            assertThat(result).containsKey(100);
            assertThat(result).doesNotContainKey(999);
        }

        @Test
        @DisplayName("skips malformed lines without crashing")
        void skipsMalformed() {
            String csv = "100,50000,10\nnot,a,number\n200,30000,5\n";
            Set<Integer> dbIds = Set.of(100, 200);

            var result = processer.parseCsvAuctions(csv, dbIds);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("skips lines with fewer than 3 columns")
        void skipsTooFewColumns() {
            String csv = "100,50000\n200,30000,5\n";
            Set<Integer> dbIds = Set.of(100, 200);

            var result = processer.parseCsvAuctions(csv, dbIds);
            assertThat(result).hasSize(1);
            assertThat(result).containsKey(200);
        }

        @Test
        @DisplayName("skips item ID 0")
        void skipsItemIdZero() {
            String csv = "0,50000,10\n200,30000,5\n";
            Set<Integer> dbIds = Set.of(0, 200);

            var result = processer.parseCsvAuctions(csv, dbIds);
            assertThat(result).hasSize(1);
            assertThat(result).containsKey(200);
        }

        @Test
        @DisplayName("aggregates multiple entries for the same item")
        void multipleEntriesSameItem() {
            String csv = "100,50000,10\n100,60000,5\n100,40000,20\n";
            Set<Integer> dbIds = Set.of(100);

            var result = processer.parseCsvAuctions(csv, dbIds);
            assertThat(result).hasSize(1);
            assertThat(result.get(100)).hasSize(3);
        }

        @Test
        @DisplayName("handles Windows-style line endings (\\r\\n)")
        void windowsLineEndings() {
            String csv = "100,50000,10\r\n200,30000,5\r\n";
            Set<Integer> dbIds = Set.of(100, 200);

            var result = processer.parseCsvAuctions(csv, dbIds);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("trims whitespace around values")
        void trimsWhitespace() {
            String csv = " 100 , 50000 , 10 \n";
            Set<Integer> dbIds = Set.of(100);

            var result = processer.parseCsvAuctions(csv, dbIds);
            assertThat(result).hasSize(1);
            assertThat(result.get(100).get(0).getUnitPrice()).isEqualTo(50000);
        }
    }

    // ── JSON Parsing ───────────────────────────────────────────────────

    @Nested
    @DisplayName("processAndCollect (JSON)")
    class ProcessAndCollectJson {

        @Test
        @DisplayName("parses valid auction JSON correctly")
        void validJson() throws IOException {
            String json = """
                {
                    "auctions": [
                        {"item": {"id": 100}, "unit_price": 50000, "quantity": 10},
                        {"item": {"id": 200}, "unit_price": 30000, "quantity": 5}
                    ]
                }
                """;

            var result = processer.processAndCollect(json, Set.of(100, 200));

            assertThat(result).hasSize(2);
            assertThat(result.get(100)).hasSize(1);
            assertThat(result.get(100).get(0).getUnitPrice()).isEqualTo(50000);
            assertThat(result.get(100).get(0).getQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("filters to DB item IDs only")
        void filtersToDbIds() throws IOException {
            String json = """
                {
                    "auctions": [
                        {"item": {"id": 100}, "unit_price": 50000, "quantity": 10},
                        {"item": {"id": 999}, "unit_price": 30000, "quantity": 5}
                    ]
                }
                """;

            var result = processer.processAndCollect(json, Set.of(100));
            assertThat(result).hasSize(1);
            assertThat(result).containsKey(100);
        }

        @Test
        @DisplayName("returns empty map for empty auctions array")
        void emptyAuctions() throws IOException {
            var result = processer.processAndCollect("{\"auctions\": []}", Set.of(100));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty map when 'auctions' field is missing")
        void missingAuctionsField() throws IOException {
            var result = processer.processAndCollect("{\"data\": \"something\"}", Set.of(100));
            assertThat(result).isEmpty();
        }
    }

    // ── Streaming Parser ───────────────────────────────────────────────

    @Nested
    @DisplayName("processAndCollectStreaming")
    class ProcessAndCollectStreaming {

        @Test
        @DisplayName("produces same results as non-streaming parser")
        void matchesNonStreaming() throws IOException {
            String json = """
                {
                    "auctions": [
                        {"item": {"id": 100}, "unit_price": 50000, "quantity": 10},
                        {"item": {"id": 200}, "unit_price": 30000, "quantity": 5},
                        {"item": {"id": 999}, "unit_price": 10000, "quantity": 1}
                    ]
                }
                """;
            Set<Integer> dbIds = Set.of(100, 200);

            var nonStreaming = processer.processAndCollect(json, dbIds);
            var streaming = processer.processAndCollectStreaming(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), dbIds);

            assertThat(streaming.keySet()).isEqualTo(nonStreaming.keySet());
            for (int key : nonStreaming.keySet()) {
                assertThat(streaming.get(key)).hasSameSizeAs(nonStreaming.get(key));
                assertThat(streaming.get(key).get(0).getUnitPrice())
                        .isEqualTo(nonStreaming.get(key).get(0).getUnitPrice());
            }
        }

        @Test
        @DisplayName("handles empty auctions array")
        void emptyAuctions() throws IOException {
            String json = "{\"auctions\": []}";
            var result = processer.processAndCollectStreaming(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Set.of(100));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("processForItems returns averages only for the requested items")
        void targetedProcessing() throws IOException {
            String json = """
                {
                    "auctions": [
                        {"item": {"id": 100}, "unit_price": 50000, "quantity": 10},
                        {"item": {"id": 100}, "unit_price": 52000, "quantity": 10},
                        {"item": {"id": 200}, "unit_price": 30000, "quantity": 5}
                    ]
                }
                """;

            var result = processer.processForItems(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                    Set.of(100)
            );

            assertThat(result).containsKey(100);
            assertThat(result).doesNotContainKey(200);
        }
    }

    // ── Average Price Calculation ──────────────────────────────────────

    @Nested
    @DisplayName("calculateAveragePrices")
    class CalculateAveragePrices {

        @Test
        @DisplayName("single entry returns its exact price and total quantity")
        void singleEntry() {
            Map<Integer, List<AuctionEntry>> auctions = new HashMap<>();
            auctions.put(100, new ArrayList<>(List.of(new AuctionEntry(50000, 10))));

            var result = processer.calculateAveragePrices(auctions);

            assertThat(result).containsKey(100);
            assertThat(result.get(100).getLeft()).isEqualTo(50000);  // avg price
            assertThat(result.get(100).getRight()).isEqualTo(10L);   // total quantity
        }

        @Test
        @DisplayName("empty entries list is skipped")
        void emptyEntries() {
            Map<Integer, List<AuctionEntry>> auctions = new HashMap<>();
            auctions.put(100, new ArrayList<>());

            var result = processer.calculateAveragePrices(auctions);
            assertThat(result).doesNotContainKey(100);
        }

        @Test
        @DisplayName("zero-quantity entries are ignored")
        void zeroQuantity() {
            Map<Integer, List<AuctionEntry>> auctions = new HashMap<>();
            auctions.put(100, new ArrayList<>(List.of(new AuctionEntry(50000, 0))));

            var result = processer.calculateAveragePrices(auctions);
            assertThat(result).doesNotContainKey(100);
        }

        @Test
        @DisplayName("weighted average favors cheapest entries (bottom 20%)")
        void favorsLowestPriced() {
            Map<Integer, List<AuctionEntry>> auctions = new HashMap<>();
            List<AuctionEntry> entries = new ArrayList<>();
            entries.add(new AuctionEntry(10000, 100));   // cheapest
            entries.add(new AuctionEntry(20000, 100));
            entries.add(new AuctionEntry(50000, 100));
            entries.add(new AuctionEntry(100000, 100));
            entries.add(new AuctionEntry(200000, 100));  // most expensive
            auctions.put(100, entries);

            var result = processer.calculateAveragePrices(auctions);

            assertThat(result).containsKey(100);
            long avgPrice = result.get(100).getLeft();
            // The weighted average of the cheapest ~20% must be lower than the naive mean
            long naiveMean = (10000 + 20000 + 50000 + 100000 + 200000) / 5;
            assertThat(avgPrice).isLessThan(naiveMean);
            assertThat(result.get(100).getRight()).isEqualTo(500L);
        }

        @Test
        @DisplayName("returns correct total quantity across all entries")
        void totalQuantity() {
            Map<Integer, List<AuctionEntry>> auctions = new HashMap<>();
            List<AuctionEntry> entries = new ArrayList<>();
            entries.add(new AuctionEntry(10000, 50));
            entries.add(new AuctionEntry(20000, 150));
            auctions.put(100, entries);

            var result = processer.calculateAveragePrices(auctions);
            assertThat(result.get(100).getRight()).isEqualTo(200L);
        }

        @Test
        @DisplayName("handles multiple items independently")
        void multipleItems() {
            Map<Integer, List<AuctionEntry>> auctions = new HashMap<>();
            auctions.put(100, new ArrayList<>(List.of(new AuctionEntry(10000, 100))));
            auctions.put(200, new ArrayList<>(List.of(new AuctionEntry(99000, 50))));

            var result = processer.calculateAveragePrices(auctions);
            assertThat(result).hasSize(2);
            assertThat(result.get(100).getLeft()).isEqualTo(10000);
            assertThat(result.get(200).getLeft()).isEqualTo(99000);
        }
    }
}
