package com.crafting.controller;

import com.crafting.model.Item;
import com.crafting.model.RecipeList;
import com.crafting.repository.ItemPriceUpdateBlacklistRepository;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.PriceSubmissionRepository;
import com.crafting.repository.RecipeListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ItemPriceUpdateBlacklistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemPriceUpdateBlacklistRepository blacklistRepository;

        @Autowired
        private PriceSubmissionRepository priceSubmissionRepository;

        @Autowired
        private RecipeListRepository recipeListRepository;

        private Long listId;

    @BeforeEach
    void setUp() {
        blacklistRepository.deleteAll();
                recipeListRepository.deleteAll();
        priceSubmissionRepository.deleteAll();
        itemRepository.deleteAll();
                RecipeList list = recipeListRepository.save(RecipeList.builder().name("Test List").build());
                listId = list.getId();
        itemRepository.save(Item.builder()
                .id(10001L)
                .name("Blacklisting Target")
                .finishingIngredient(false)
                .build());
    }

    @Nested
    @DisplayName("CRUD")
    class Crud {

        @Test
        @DisplayName("ADMIN can add, list, and remove blacklist entry")
        void adminCrud() throws Exception {
            mockMvc.perform(post("/recipe-lists/{listId}/item-price-blacklist/{itemId}", listId, 10001L)
                            .with(user("4242").roles("ADMIN")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.listId", is(listId.intValue())))
                    .andExpect(jsonPath("$.itemId", is(10001)))
                    .andExpect(jsonPath("$.itemName", is("Blacklisting Target")));

            mockMvc.perform(get("/recipe-lists/{listId}/item-price-blacklist", listId)
                            .with(user("4242").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].itemId", is(10001)));

            mockMvc.perform(delete("/recipe-lists/{listId}/item-price-blacklist/{itemId}", listId, 10001L)
                            .with(user("4242").roles("ADMIN")))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/recipe-lists/{listId}/item-price-blacklist", listId)
                            .with(user("4242").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("adding non-existent item returns 404")
        void addingMissingItemReturnsNotFound() throws Exception {
                        mockMvc.perform(post("/recipe-lists/{listId}/item-price-blacklist/{itemId}", listId, 99999L)
                            .with(user("4242").roles("ADMIN")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", is("Item not found: 99999")));
        }
    }

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @DisplayName("ALLOWED_USER cannot read blacklist")
        void allowedUserDeniedGet() throws Exception {
                        mockMvc.perform(get("/recipe-lists/{listId}/item-price-blacklist", listId)
                            .with(SecurityMockMvcRequestPostProcessors.user("1001").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ALLOWED_USER cannot mutate blacklist")
        void allowedUserDeniedMutations() throws Exception {
            mockMvc.perform(post("/recipe-lists/{listId}/item-price-blacklist/{itemId}", listId, 10001L)
                            .with(user("1001").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete("/recipe-lists/{listId}/item-price-blacklist/{itemId}", listId, 10001L)
                            .with(user("1001").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());
        }
    }
}
