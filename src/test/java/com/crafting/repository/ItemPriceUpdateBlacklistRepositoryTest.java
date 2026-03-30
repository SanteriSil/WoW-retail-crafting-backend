package com.crafting.repository;

import com.crafting.model.Item;
import com.crafting.model.ItemPriceUpdateBlacklistEntry;
import com.crafting.model.RecipeList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ItemPriceUpdateBlacklistRepositoryTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemPriceUpdateBlacklistRepository blacklistRepository;

        @Autowired
        private RecipeListRepository recipeListRepository;

    @BeforeEach
    void setUp() {
        blacklistRepository.deleteAll();
                recipeListRepository.deleteAll();
        itemRepository.deleteAll();
    }

    @Test
    @DisplayName("persists and returns blacklisted item IDs")
    void persistsBlacklistEntries() {
        Item item = itemRepository.save(Item.builder()
                .id(12345L)
                .name("Test Item")
                .finishingIngredient(false)
                .build());
        RecipeList list = recipeListRepository.save(RecipeList.builder().name("List A").build());

        blacklistRepository.save(ItemPriceUpdateBlacklistEntry.builder()
                .recipeList(list)
                .item(item)
                .build());

        assertThat(blacklistRepository.findItemIdsByListId(list.getId())).containsExactly(12345L);
        assertThat(blacklistRepository.existsByRecipeList_IdAndItem_Id(list.getId(), 12345L)).isTrue();
    }

    @Test
    @DisplayName("enforces unique item constraint")
    void enforcesUniqueItemConstraint() {
        Item item = itemRepository.save(Item.builder()
                .id(9999L)
                .name("Unique Item")
                .finishingIngredient(false)
                .build());
        RecipeList listA = recipeListRepository.save(RecipeList.builder().name("List A").build());
        RecipeList listB = recipeListRepository.save(RecipeList.builder().name("List B").build());

        blacklistRepository.saveAndFlush(ItemPriceUpdateBlacklistEntry.builder()
                .recipeList(listA)
                .item(item)
                .build());

        blacklistRepository.saveAndFlush(ItemPriceUpdateBlacklistEntry.builder()
                .recipeList(listB)
                .item(item)
                .build());

        assertThatThrownBy(() -> blacklistRepository.saveAndFlush(ItemPriceUpdateBlacklistEntry.builder()
                .recipeList(listA)
                .item(item)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
