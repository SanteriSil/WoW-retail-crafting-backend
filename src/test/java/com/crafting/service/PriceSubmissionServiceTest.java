package com.crafting.service;

import com.crafting.auth.ActorContextService;
import com.crafting.blizz.Pair;
import com.crafting.model.Item;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.PriceSubmissionRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PriceSubmissionServiceTest {

    @Autowired
    private PriceSubmissionService priceSubmissionService;

    @Autowired
    private PriceSubmissionRepository priceSubmissionRepository;

    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        priceSubmissionRepository.deleteAll();
        Item item = itemRepository.findById(100L)
                .orElseGet(() -> Item.builder().id(100L).build());
        item.setName("Test Item");
        item.setFinishingIngredient(false);
        itemRepository.save(item);
    }

    @Test
    @DisplayName("records append-only rows for repeated submissions")
    void appendOnlyWrites() {
        var actor = new ActorContextService.ActorSnapshot(4242L, "tester");
        Map<Integer, Pair<Long, Long>> payload = Map.of(100, new Pair<>(12345L, 50L));

        int first = priceSubmissionService.recordAddonSubmissionBatch(payload, actor);
        int second = priceSubmissionService.recordAddonSubmissionBatch(payload, actor);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(priceSubmissionRepository.count()).isEqualTo(2);

        var rows = priceSubmissionRepository.findAll();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getSource()).isEqualTo(PriceSubmissionService.SOURCE_USER_ADDON_SUBMISSION);
            assertThat(row.getActorDiscordId()).isEqualTo(4242L);
            assertThat(row.getActorDiscordUsername()).isEqualTo("tester");
            assertThat(row.getAuditEventId()).isNotNull();
            assertThat(row.getBatchId()).isNotBlank();
        });
    }
}
