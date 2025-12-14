package com.crafting.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.crafting.repository.ItemRepository;
import com.crafting.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@SpringBootTest
@Transactional
class EntityPersistenceTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Test
    void saveAndLoadItem_withCurrentPrice() {
        Item item = new Item(123L, "Test Item");
        item.setCurrentPrice(1500L);
        item.setCurrentPriceRecordedAt(OffsetDateTime.now());

        itemRepository.save(item);

        Item found = itemRepository.findById(123L).orElseThrow();
        assertThat(found.getCurrentPrice()).isEqualTo(1500L);
        assertThat(found.getCurrentPriceRecordedAt()).isNotNull();
    }

    @Test
    void saveAndLoadRecipe_withOutputQuantity() {
        Item item = new Item(555L, "Output Item");
        itemRepository.save(item);

        Recipe recipe = new Recipe("Test Recipe", item, "[]");
        // default outputQuantity is 1.0
        recipeRepository.save(recipe);

        Recipe found = recipeRepository.findById(recipe.getId()).orElseThrow();
        assertThat(found.getOutputQuantity()).isEqualTo(1.0f);
    }

}
