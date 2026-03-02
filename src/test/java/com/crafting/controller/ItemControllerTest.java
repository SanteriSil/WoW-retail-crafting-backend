package com.crafting.controller;

import com.crafting.cache.CachedResult;
import com.crafting.model.Item;
import com.crafting.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Integration tests for {@link ItemController}.
 * Uses full Spring context with H2 in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ItemControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ItemRepository itemRepository;
    @Autowired private CachedResult<List<Item>> itemCache;
    @Autowired private CachedResult<List<Long>> itemIdCache;

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        itemCache.invalidate();
        itemIdCache.invalidate();
    }

    private Item testItem(Long id, String name) {
        return Item.builder().id(id).name(name).finishingIngredient(false).build();
    }

    // ── Read operations (public) ───────────────────────────────────────

    @Nested
    @DisplayName("GET /items")
    class GetAllItems {

        @Test
        @DisplayName("returns empty list when no items exist")
        void emptyList() throws Exception {
            mockMvc.perform(get("/items"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("returns all items")
        void returnsAll() throws Exception {
            itemRepository.save(testItem(100L, "Dust"));
            itemRepository.save(testItem(200L, "Ore"));
            itemCache.invalidate();

            mockMvc.perform(get("/items"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].name", containsInAnyOrder("Dust", "Ore")));
        }
    }

    @Nested
    @DisplayName("GET /items/ids")
    class GetAllItemIds {

        @Test
        @DisplayName("returns empty list when no items exist")
        void emptyList() throws Exception {
            mockMvc.perform(get("/items/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("returns all IDs")
        void returnsAllIds() throws Exception {
            itemRepository.save(testItem(100L, "Dust"));
            itemRepository.save(testItem(200L, "Ore"));
            itemIdCache.invalidate();

            mockMvc.perform(get("/items/ids"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$", containsInAnyOrder(100, 200)));
        }
    }

    @Nested
    @DisplayName("GET /items/ordered")
    class GetItemsOrdered {

        @Test
        @DisplayName("returns items in requested ID order")
        void orderedByRequestedIds() throws Exception {
            itemRepository.save(testItem(100L, "Dust"));
            itemRepository.save(testItem(200L, "Ore"));
            itemRepository.save(testItem(300L, "Bar"));

            mockMvc.perform(get("/items/ordered")
                            .param("ids", "300", "100", "200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].name", is("Bar")))
                    .andExpect(jsonPath("$[1].name", is("Dust")))
                    .andExpect(jsonPath("$[2].name", is("Ore")));
        }
    }

    // ── Create ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /items")
    class CreateItem {

        @Test
        @DisplayName("creates item → 201")
        void createsItem() throws Exception {
            String json = "{\"id\":12345,\"name\":\"Enchanted Dust\",\"finishingIngredient\":false}";

            mockMvc.perform(post("/items")
                            .with(user("testuser").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(12345)))
                    .andExpect(jsonPath("$.name", is("Enchanted Dust")));

            assertThat(itemRepository.existsById(12345L)).isTrue();
        }

        @Test
        @DisplayName("duplicate ID → 409 Conflict")
        void duplicateId() throws Exception {
            itemRepository.save(testItem(100L, "Existing"));

            String json = "{\"id\":100,\"name\":\"Duplicate\",\"finishingIngredient\":false}";
            mockMvc.perform(post("/items")
                            .with(user("testuser").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("missing name → 400 Bad Request (validation)")
        void missingName() throws Exception {
            String json = "{\"id\": 999}";

            mockMvc.perform(post("/items")
                            .with(user("testuser").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("invalidates cache after creation")
        void invalidatesCache() throws Exception {
            // Prime the cache with an empty list
            mockMvc.perform(get("/items"))
                    .andExpect(jsonPath("$", hasSize(0)));

            // Create an item
            String json = "{\"id\":100,\"name\":\"New Item\",\"finishingIngredient\":false}";
            mockMvc.perform(post("/items")
                            .with(user("testuser").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());

            // Cache should be invalidated → GET should return the new item
            mockMvc.perform(get("/items"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name", is("New Item")));
        }
    }

    // ── Update ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /items/{id}")
    class UpdateItem {

        @Test
        @DisplayName("updates existing item → 200")
        void updatesItem() throws Exception {
            itemRepository.save(testItem(100L, "Old Name"));

            String json = "{\"id\":100,\"name\":\"New Name\",\"finishingIngredient\":false}";
            mockMvc.perform(put("/items/100")
                            .with(user("testuser").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("New Name")));

            Item fromDb = itemRepository.findById(100L).orElseThrow();
            assertThat(fromDb.getName()).isEqualTo("New Name");
        }

        @Test
        @DisplayName("non-existent ID → 404")
        void notFound() throws Exception {
            String json = "{\"id\":99999,\"name\":\"Ghost\",\"finishingIngredient\":false}";
            mockMvc.perform(put("/items/99999")
                            .with(user("testuser").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isNotFound());
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /items/{id}")
    class DeleteItem {

        @Test
        @DisplayName("deletes existing item → 204")
        void deletesItem() throws Exception {
            itemRepository.save(testItem(100L, "Doomed"));

            mockMvc.perform(delete("/items/100")
                            .with(user("testuser").roles("ADMIN")))
                    .andExpect(status().isNoContent());

            assertThat(itemRepository.existsById(100L)).isFalse();
        }

        @Test
        @DisplayName("non-existent ID → 404")
        void notFound() throws Exception {
            mockMvc.perform(delete("/items/99999")
                            .with(user("testuser").roles("ADMIN")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("invalidates cache after deletion")
        void invalidatesCache() throws Exception {
            itemRepository.save(testItem(100L, "Temporary"));
            itemCache.invalidate();

            // Prime the cache
            mockMvc.perform(get("/items"))
                    .andExpect(jsonPath("$", hasSize(1)));

            // Delete
            mockMvc.perform(delete("/items/100")
                            .with(user("testuser").roles("ADMIN")))
                    .andExpect(status().isNoContent());

            // Cache should be invalidated → GET returns empty
            mockMvc.perform(get("/items"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }
}
