package com.crafting.repository;

import com.crafting.model.Item;
import com.crafting.model.Profession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replaces the original ItemRepositoryImplTest which was broken —
 * it referenced a deleted {@code ItemDTO} class and a removed
 * {@code findAllDtos()} method. See PLAN.md §10.4 regression strategy.
 *
 * Tests the custom query methods on {@link ItemRepository} using
 * an H2 in-memory database with ddl-auto=create-drop.
 */
@DataJpaTest
class ItemRepositoryTest {

    @Autowired private ItemRepository itemRepository;
    @Autowired private ProfessionRepository professionRepository;

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        professionRepository.deleteAll();
    }

    private Item item(Long id, String name) {
        return Item.builder().id(id).name(name).finishingIngredient(false).build();
    }

    // ── Basic CRUD ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Basic CRUD")
    class BasicCrud {

        @Test
        @DisplayName("saves and retrieves item by ID")
        void saveAndFind() {
            Item saved = itemRepository.save(item(100L, "Dust"));
            Item found = itemRepository.findById(100L).orElseThrow();

            assertThat(found.getId()).isEqualTo(100L);
            assertThat(found.getName()).isEqualTo("Dust");
            assertThat(found.isFinishingIngredient()).isFalse();
        }

        @Test
        @DisplayName("findAll returns all saved items")
        void findAll() {
            itemRepository.save(item(100L, "Dust"));
            itemRepository.save(item(200L, "Ore"));

            List<Item> all = itemRepository.findAll();
            assertThat(all).hasSize(2)
                    .extracting(Item::getName)
                    .containsExactlyInAnyOrder("Dust", "Ore");
        }

        @Test
        @DisplayName("existsById returns true for existing, false for missing")
        void existsById() {
            itemRepository.save(item(100L, "Dust"));

            assertThat(itemRepository.existsById(100L)).isTrue();
            assertThat(itemRepository.existsById(999L)).isFalse();
        }

        @Test
        @DisplayName("deleteById removes the item")
        void deleteById() {
            itemRepository.save(item(100L, "Dust"));
            itemRepository.deleteById(100L);

            assertThat(itemRepository.findById(100L)).isEmpty();
        }
    }

    // ── Custom queries ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Custom query: findAllIds")
    class FindAllIds {

        @Test
        @DisplayName("returns all IDs as a list")
        void returnsAllIds() {
            itemRepository.save(item(100L, "Dust"));
            itemRepository.save(item(200L, "Ore"));
            itemRepository.save(item(300L, "Bar"));

            List<Long> ids = itemRepository.findAllIds();
            assertThat(ids).containsExactlyInAnyOrder(100L, 200L, 300L);
        }

        @Test
        @DisplayName("returns empty list when no items")
        void emptyWhenNoItems() {
            assertThat(itemRepository.findAllIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Custom query: findByIconUrlIsNullOrIconUrl")
    class FindByIconUrl {

        @Test
        @DisplayName("returns items with null iconUrl")
        void returnsNullIconUrl() {
            Item noIcon = item(100L, "No Icon");
            // iconUrl defaults to null
            itemRepository.save(noIcon);

            Item hasIcon = item(200L, "Has Icon");
            hasIcon.setIconUrl("https://example.com/icon.png");
            itemRepository.save(hasIcon);

            List<Item> result = itemRepository.findByIconUrlIsNullOrIconUrl("non-matching");
            assertThat(result).hasSize(1)
                    .extracting(Item::getName)
                    .containsExactly("No Icon");
        }

        @Test
        @DisplayName("returns items with matching iconUrl")
        void returnsMatchingIconUrl() {
            Item matching = item(100L, "Matching");
            matching.setIconUrl("target-url");
            itemRepository.save(matching);

            Item different = item(200L, "Different");
            different.setIconUrl("other-url");
            itemRepository.save(different);

            List<Item> result = itemRepository.findByIconUrlIsNullOrIconUrl("target-url");
            assertThat(result).hasSize(1)
                    .extracting(Item::getName)
                    .containsExactly("Matching");
        }

        @Test
        @DisplayName("returns items with null OR matching iconUrl")
        void returnsBoth() {
            Item noIcon = item(100L, "No Icon");
            itemRepository.save(noIcon);

            Item matching = item(200L, "Matching");
            matching.setIconUrl("target-url");
            itemRepository.save(matching);

            Item different = item(300L, "Different");
            different.setIconUrl("other-url");
            itemRepository.save(different);

            List<Item> result = itemRepository.findByIconUrlIsNullOrIconUrl("target-url");
            assertThat(result).hasSize(2)
                    .extracting(Item::getName)
                    .containsExactlyInAnyOrder("No Icon", "Matching");
        }
    }

    // ── Profession relation ────────────────────────────────────────────

    @Nested
    @DisplayName("Item ↔ Profession relationship")
    class ProfessionRelation {

        @Test
        @DisplayName("saves item with profession and retrieves relationship")
        void itemWithProfession() {
            Profession prof = Profession.builder().name("Enchanting").build();
            prof = professionRepository.save(prof);

            Item item = item(100L, "Dust");
            item.setProfession(prof);
            itemRepository.save(item);

            Item found = itemRepository.findById(100L).orElseThrow();
            assertThat(found.getProfession()).isNotNull();
            assertThat(found.getProfession().getName()).isEqualTo("Enchanting");
        }

        @Test
        @DisplayName("item without profession has null profession")
        void itemWithoutProfession() {
            itemRepository.save(item(100L, "Raw Ore"));

            Item found = itemRepository.findById(100L).orElseThrow();
            assertThat(found.getProfession()).isNull();
        }
    }
}
