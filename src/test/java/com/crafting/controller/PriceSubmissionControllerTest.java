package com.crafting.controller;

import com.crafting.auth.ActorContextService;
import com.crafting.blizz.Pair;
import com.crafting.model.Item;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.PriceSubmissionRepository;
import com.crafting.service.PriceSubmissionService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PriceSubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PriceSubmissionService priceSubmissionService;

    @Autowired
    private PriceSubmissionRepository priceSubmissionRepository;

    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        priceSubmissionRepository.deleteAll();
        itemRepository.deleteAll();
        itemRepository.save(Item.builder().id(200L).name("History Item").finishingIngredient(false).build());

        priceSubmissionService.recordAddonSubmissionBatch(
            Map.of(200, new Pair<>(7777L, 30L)),
                new ActorContextService.ActorSnapshot(9001L, "history-user")
        );
    }

    @Nested
    @DisplayName("GET /price-submissions")
    class History {

        @Test
        @DisplayName("ADMIN can view filtered history")
        void adminCanViewHistory() throws Exception {
            mockMvc.perform(get("/price-submissions")
                            .with(user("4242").roles("ADMIN"))
                            .param("source", "USER_ADDON_SUBMISSION"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].itemId", is(200)))
                    .andExpect(jsonPath("$.content[0].source", is("USER_ADDON_SUBMISSION")))
                    .andExpect(jsonPath("$.content[0].actorDiscordId", is(9001)));
        }

        @Test
        @DisplayName("ALLOWED_USER is forbidden")
        void allowedUserForbidden() throws Exception {
            mockMvc.perform(get("/price-submissions")
                            .with(user("1001").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());
        }
    }
}
