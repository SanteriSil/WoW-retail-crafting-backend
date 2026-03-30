package com.crafting.controller;

import com.crafting.blizz.AHDataFetcher;
import com.crafting.blizz.Pair;
import com.crafting.model.Item;
import com.crafting.repository.ItemRepository;
import com.crafting.repository.PriceSubmissionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuctionSubmissionAttributionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private PriceSubmissionRepository priceSubmissionRepository;

    @MockitoBean
    private AHDataFetcher ahDataFetcher;

    @BeforeEach
    void setUp() {
        priceSubmissionRepository.deleteAll();
        itemRepository.deleteAll();
        itemRepository.save(Item.builder().id(300L).name("Submitted Item").finishingIngredient(false).build());
    }

    @Test
    @DisplayName("addon auction submit stores actor-attributed submission history")
    void addonSubmitStoresAttribution() throws Exception {
        when(ahDataFetcher.submitAuctionDataDetailed(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(new AHDataFetcher.SubmissionResult(1, Map.of(300, new Pair<>(5555L, 42L))));

        var auth = new UsernamePasswordAuthenticationToken(
                "4242",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        auth.setDetails(Map.of("discordUsername", "submitter"));

        mockMvc.perform(post("/craftingAH/submit")
                        .with(authentication(auth))
                        .contentType(TEXT_PLAIN)
                        .content("300,5555,42"))
                .andExpect(status().isOk());

        assertThat(priceSubmissionRepository.count()).isEqualTo(1);
        var row = priceSubmissionRepository.findAll().getFirst();
        assertThat(row.getItem().getId()).isEqualTo(300L);
        assertThat(row.getSubmittedPrice()).isEqualTo(5555L);
        assertThat(row.getSubmittedQuantity()).isEqualTo(42L);
        assertThat(row.getSource()).isEqualTo("USER_ADDON_SUBMISSION");
        assertThat(row.getActorDiscordId()).isEqualTo(4242L);
        assertThat(row.getActorDiscordUsername()).isEqualTo("submitter");
        assertThat(row.getAuditEventId()).isNotNull();
    }
}
