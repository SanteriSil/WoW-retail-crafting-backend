package com.crafting.controller;

import com.crafting.model.AllowedUser;
import com.crafting.repository.AllowedUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Integration test verifying the SecurityConfig authorization rules (PLAN.md §4.4).
 *
 * Permission matrix under test:
 *   PUBLIC        — no auth needed
 *   ALLOWED_USER+ — GET /recipes, /export, /auth/me
 *   ADMIN+        — write operations on items/recipes, /auth/users, /logs, /craftingAH
 *   OWNER only    — /auth/users/{id}/promote, /auth/users/{id}/demote
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityEndpointAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AllowedUserRepository allowedUserRepository;

    @AfterEach
    void cleanUp() {
        // Remove test users seeded during role-based tests
        allowedUserRepository.deleteById(99999L);
        allowedUserRepository.deleteById(555L);
    }

    // ── Public endpoints ───────────────────────────────────────────────

    @Nested
    @DisplayName("Public endpoints — accessible without authentication")
    class PublicEndpoints {

        @Test
        @DisplayName("GET /health → 200")
        void health() throws Exception {
            mockMvc.perform(get("/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /items → 200")
        void getItems() throws Exception {
            mockMvc.perform(get("/items"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /items/ids → 200")
        void getItemIds() throws Exception {
            mockMvc.perform(get("/items/ids"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /items/ordered?ids=1 → not 401 (may be 200 with empty result)")
        void getItemsOrdered() throws Exception {
            mockMvc.perform(get("/items/ordered").param("ids", "1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /professions → 200")
        void getProfessions() throws Exception {
            mockMvc.perform(get("/professions"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /expansions → 200")
        void getExpansions() throws Exception {
            mockMvc.perform(get("/expansions"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /auth/access-requests → not 401 (public endpoint, may be 400)")
        void accessRequestPublic() throws Exception {
            mockMvc.perform(post("/auth/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"discordId\":\"12345\",\"discordUsername\":\"testuser\"}"))
                    .andExpect(result ->
                            org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                                    .isNotEqualTo(401));
        }

        @Test
        @DisplayName("POST /auth/discord/callback → not 401 (400 expected for missing body)")
        void discordCallback() throws Exception {
            mockMvc.perform(post("/auth/discord/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is4xxClientError())
                    // Must NOT be 401 — this endpoint is public
                    .andExpect(result ->
                            org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                                    .isNotEqualTo(401));
        }
    }

    // ── Protected endpoints ────────────────────────────────────────────

    @Nested
    @DisplayName("Protected endpoints — require authentication")
    class ProtectedEndpoints {

        // ── Items (write operations) ──

        @Test
        @DisplayName("POST /items → 401 without auth")
        void createItemNoAuth() throws Exception {
            mockMvc.perform(post("/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"name\":\"Test\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /items/1 → 401 without auth")
        void updateItemNoAuth() throws Exception {
            mockMvc.perform(put("/items/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"name\":\"Test\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /items/1 → 401 without auth")
        void deleteItemNoAuth() throws Exception {
            mockMvc.perform(delete("/items/1"))
                    .andExpect(status().isUnauthorized());
        }

        // ── User management ──

        @Test
        @DisplayName("GET /auth/users → 401 without auth")
        void getUsersNoAuth() throws Exception {
            mockMvc.perform(get("/auth/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /auth/users → 401 without auth")
        void addUserNoAuth() throws Exception {
            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"discordId\":\"123\",\"discordUsername\":\"test\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /auth/users/123 → 401 without auth")
        void removeUserNoAuth() throws Exception {
            mockMvc.perform(delete("/auth/users/123"))
                    .andExpect(status().isUnauthorized());
        }

        // ── Logs ──

        @Test
        @DisplayName("GET /logs/current → 401 without auth")
        void getLogsNoAuth() throws Exception {
            mockMvc.perform(get("/logs/current"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /logs/archive → 401 without auth")
        void archiveLogsNoAuth() throws Exception {
            mockMvc.perform(post("/logs/archive"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /logs/clear → 401 without auth")
        void clearLogsNoAuth() throws Exception {
            mockMvc.perform(post("/logs/clear"))
                    .andExpect(status().isUnauthorized());
        }

        // ── AH fetch ──

        @Test
        @DisplayName("GET /craftingAH/fetch → 401 without auth")
        void ahFetchNoAuth() throws Exception {
            mockMvc.perform(get("/craftingAH/fetch"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /craftingAH/submit → 401 without auth")
        void ahSubmitNoAuth() throws Exception {
            mockMvc.perform(post("/craftingAH/submit")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("100,50000,10"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /craftingAH/fetch-for-recipes → 401 without auth")
        void ahFetchForRecipesNoAuth() throws Exception {
            mockMvc.perform(post("/craftingAH/fetch-for-recipes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"recipeIds\":[1]}"))
                .andExpect(status().isUnauthorized());
        }

        // ── Recipes (read = ALLOWED_USER+, write = ADMIN+) ──

        @Test
        @DisplayName("GET /recipes → 401 without auth")
        void getRecipesNoAuth() throws Exception {
            mockMvc.perform(get("/recipes"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /recipes/spell-ids → 401 without auth")
        void getSpellIdsNoAuth() throws Exception {
            mockMvc.perform(get("/recipes/spell-ids"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /recipes/item-ids → 401 without auth")
        void getRecipeItemIdsNoAuth() throws Exception {
            mockMvc.perform(get("/recipes/item-ids"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /recipes → 401 without auth")
        void createRecipeNoAuth() throws Exception {
            mockMvc.perform(post("/recipes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /recipes/1 → 401 without auth")
        void updateRecipeNoAuth() throws Exception {
            mockMvc.perform(put("/recipes/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE /recipes/1 → 401 without auth")
        void deleteRecipeNoAuth() throws Exception {
            mockMvc.perform(delete("/recipes/1"))
                    .andExpect(status().isUnauthorized());
        }

        // ── Characters (ALLOWED_USER+) ──

        @Test
        @DisplayName("GET /characters → 401 without auth")
        void getCharactersNoAuth() throws Exception {
            mockMvc.perform(get("/characters"))
                    .andExpect(status().isUnauthorized());
        }

        // ── Dashboard (ALLOWED_USER+) ──

        @Test
        @DisplayName("GET /dashboard → 401 without auth")
        void getDashboardNoAuth() throws Exception {
            mockMvc.perform(get("/dashboard"))
                    .andExpect(status().isUnauthorized());
        }

        // ── Export (ALLOWED_USER+) ──

        @Test
        @DisplayName("GET /export/excel → 401 without auth")
        void getExportNoAuth() throws Exception {
            mockMvc.perform(get("/export/excel"))
                    .andExpect(status().isUnauthorized());
        }

        // ── Access requests (GET = ADMIN+) ──

        @Test
        @DisplayName("GET /auth/access-requests → 401 without auth")
        void getAccessRequestsNoAuth() throws Exception {
            mockMvc.perform(get("/auth/access-requests"))
                    .andExpect(status().isUnauthorized());
        }

        // ── Scraper (ADMIN+) ──

        @Test
        @DisplayName("POST /scraper/items → 401 without auth")
        void scraperNoAuth() throws Exception {
            mockMvc.perform(post("/scraper/items"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── Authenticated access ───────────────────────────────────────────

    @Nested
    @DisplayName("Protected endpoints — accessible with ADMIN role")
    class AuthenticatedAccess {

        @Test
        @DisplayName("POST /items → not 401/403 with ADMIN (may be 201 or 400)")
        void createItemWithAuth() throws Exception {
            mockMvc.perform(post("/items")
                            .with(user("12345").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":99999,\"name\":\"AuthTest Item\"}"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("GET /auth/users → not 401/403 with ADMIN")
        void getUsersWithAuth() throws Exception {
            mockMvc.perform(get("/auth/users")
                            .with(user("12345").roles("ADMIN")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("DELETE /items/99999 → not 401/403 with ADMIN (may be 404)")
        void deleteItemWithAuth() throws Exception {
            mockMvc.perform(delete("/items/99999")
                            .with(user("12345").roles("ADMIN")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }
    }

    // ── Role-based access rules ────────────────────────────────────────

    @Nested
    @DisplayName("Role-based access — PLAN.md §4.4 permission matrix")
    class RoleBasedAccess {

        @Test
        @DisplayName("GET /auth/me → not 401/403 with ALLOWED_USER")
        void getMeAllowedUser() throws Exception {
            // Seed the user so me() can find them in the DB
            allowedUserRepository.save(new AllowedUser(99999L, "test-user"));
            mockMvc.perform(get("/auth/me")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("POST /auth/users → 403 with ALLOWED_USER (requires ADMIN)")
        void addUserForbiddenForAllowedUser() throws Exception {
            mockMvc.perform(post("/auth/users")
                            .with(user("99999").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"discordId\":\"123\",\"discordUsername\":\"test\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /auth/users/{id}/promote → 403 with ADMIN (requires OWNER)")
        void promoteForbiddenForAdmin() throws Exception {
            mockMvc.perform(post("/auth/users/123/promote")
                            .with(user("99999").roles("ADMIN")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /auth/users/{id}/demote → 403 with ADMIN (requires OWNER)")
        void demoteForbiddenForAdmin() throws Exception {
            mockMvc.perform(post("/auth/users/123/demote")
                            .with(user("99999").roles("ADMIN")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /auth/users/{id}/promote → not 401/403 with OWNER (may be 404)")
        void promoteAccessibleForOwner() throws Exception {
            mockMvc.perform(post("/auth/users/123/promote")
                            .with(user("99999").roles("OWNER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("POST /auth/users → not 401/403 with OWNER")
        void addUserAccessibleForOwner() throws Exception {
            mockMvc.perform(post("/auth/users")
                            .with(user("99999").roles("OWNER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"discordId\":\"555\",\"discordUsername\":\"ownertest\"}"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("POST /items → 403 with ALLOWED_USER (requires ADMIN)")
        void createItemForbiddenForAllowedUser() throws Exception {
            mockMvc.perform(post("/items")
                            .with(user("99999").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"name\":\"Test\"}"))
                    .andExpect(status().isForbidden());
        }

        // ── Recipes: ALLOWED_USER can read, but not write ──

        @Test
        @DisplayName("GET /recipes → not 401/403 with ALLOWED_USER")
        void getRecipesAllowedUser() throws Exception {
            mockMvc.perform(get("/recipes")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("GET /recipes/spell-ids → not 401/403 with ALLOWED_USER")
        void getSpellIdsAllowedUser() throws Exception {
            mockMvc.perform(get("/recipes/spell-ids")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

                    @Test
                    @DisplayName("GET /recipes/item-ids → 403 with ALLOWED_USER (requires ADMIN)")
                    void getRecipeItemIdsForbiddenForAllowedUser() throws Exception {
                        mockMvc.perform(get("/recipes/item-ids")
                                .with(user("99999").roles("ALLOWED_USER")))
                            .andExpect(status().isForbidden());
                    }

                    @Test
                    @DisplayName("GET /recipes/item-ids → not 401/403 with ADMIN")
                    void getRecipeItemIdsAccessibleForAdmin() throws Exception {
                        mockMvc.perform(get("/recipes/item-ids")
                                .with(user("99999").roles("ADMIN")))
                            .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                            });
                    }

        @Test
        @DisplayName("POST /recipes → 403 with ALLOWED_USER (requires ADMIN)")
        void createRecipeForbiddenForAllowedUser() throws Exception {
            mockMvc.perform(post("/recipes")
                            .with(user("99999").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE /recipes/1 → 403 with ALLOWED_USER (requires ADMIN)")
        void deleteRecipeForbiddenForAllowedUser() throws Exception {
            mockMvc.perform(delete("/recipes/1")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());
        }

        // ── Characters: ALLOWED_USER+ can access ──

        @Test
        @DisplayName("GET /characters → not 401/403 with ALLOWED_USER")
        void getCharactersAllowedUser() throws Exception {
            mockMvc.perform(get("/characters")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        // ── Dashboard: ALLOWED_USER+ can access ──

        @Test
        @DisplayName("GET /dashboard → not 401/403 with ALLOWED_USER")
        void getDashboardAllowedUser() throws Exception {
            mockMvc.perform(get("/dashboard")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        // ── Export: ALLOWED_USER+ can access ──

        @Test
        @DisplayName("GET /export/excel → not 401/403 with ALLOWED_USER")
        void getExportAllowedUser() throws Exception {
            mockMvc.perform(get("/export/excel")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        // ── Access requests: ALLOWED_USER cannot list, ADMIN can ──

        @Test
        @DisplayName("GET /auth/access-requests → 403 with ALLOWED_USER (requires ADMIN)")
        void getAccessRequestsForbiddenForAllowedUser() throws Exception {
            mockMvc.perform(get("/auth/access-requests")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /auth/access-requests → not 401/403 with ADMIN")
        void getAccessRequestsForAdmin() throws Exception {
            mockMvc.perform(get("/auth/access-requests")
                            .with(user("99999").roles("ADMIN")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        // ── Logs: ALLOWED_USER cannot access ──

        @Test
        @DisplayName("GET /logs/current → 403 with ALLOWED_USER (requires ADMIN)")
        void getLogsForbiddenForAllowedUser() throws Exception {
            mockMvc.perform(get("/logs/current")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());
        }

        // ── craftingAH: ALLOWED_USER cannot access ──

        @Test
        @DisplayName("GET /craftingAH/fetch → 403 with ALLOWED_USER (requires ADMIN)")
        void ahFetchForbiddenForAllowedUser() throws Exception {
            mockMvc.perform(get("/craftingAH/fetch")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /craftingAH/fetch-for-recipes → 403 with ALLOWED_USER (requires ADMIN)")
        void ahFetchForRecipesForbiddenForAllowedUser() throws Exception {
            mockMvc.perform(post("/craftingAH/fetch-for-recipes")
                            .with(user("99999").roles("ALLOWED_USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recipeIds\":[1]}"))
                    .andExpect(status().isForbidden());
        }
    }
}
