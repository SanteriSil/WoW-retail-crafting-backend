package com.crafting.controller;

import com.crafting.blizz.AHDataFetcher;
import com.crafting.service.RecipeService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AHFetchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AHDataFetcher ahDataFetcher;

    @MockitoBean
    private RecipeService recipeService;

    @Test
    @DisplayName("refreshes targeted recipe items")
    void refreshesTargetedItems() throws Exception {
        when(recipeService.getTrackedItemIdsForRecipes(List.of(1L))).thenReturn(Set.of(100, 200));
        when(ahDataFetcher.triggerFetchForItems(new LinkedHashSet<>(Set.of(100, 200)))).thenReturn(2);

        mockMvc.perform(post("/craftingAH/fetch-for-recipes")
                        .with(user("4242").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount", is(2)))
                .andExpect(jsonPath("$.itemIds", containsInAnyOrder(100, 200)));

        verify(ahDataFetcher).triggerFetchForItems(new LinkedHashSet<>(Set.of(100, 200)));
    }

    @Test
    @DisplayName("returns 400 when no tracked items found")
    void noTrackedItemsReturnsBadRequest() throws Exception {
        when(recipeService.getTrackedItemIdsForRecipes(List.of(1L))).thenReturn(Set.of());

        mockMvc.perform(post("/craftingAH/fetch-for-recipes")
                        .with(user("4242").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeIds\":[1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("No tracked items found for the selected recipes")));

        verify(ahDataFetcher, never()).triggerFetchForItems(anySet());
    }
}
