package com.crafting.controller;

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
 * Integration test verifying the SecurityConfig authorization rules.
 *
 * Tests the current security model:
 * - Public endpoints are accessible without authentication (no 401/403)
 * - Protected endpoints reject unauthenticated requests (401)
 * - Protected endpoints accept authenticated requests (not 401/403)
 *
 * When the role-based auth system (PLAN.md §4) is implemented, this test
 * must be updated to cover OWNER/ADMIN/ALLOWED_USER/PUBLIC permission matrix.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityEndpointAccessTest {

    @Autowired
    private MockMvc mockMvc;

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
        @DisplayName("POST /items → 403 without auth")
        void createItemNoAuth() throws Exception {
            mockMvc.perform(post("/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"name\":\"Test\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /items/1 → 403 without auth")
        void updateItemNoAuth() throws Exception {
            mockMvc.perform(put("/items/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"name\":\"Test\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE /items/1 → 403 without auth")
        void deleteItemNoAuth() throws Exception {
            mockMvc.perform(delete("/items/1"))
                    .andExpect(status().isForbidden());
        }

        // ── User management ──

        @Test
        @DisplayName("GET /auth/users → 403 without auth")
        void getUsersNoAuth() throws Exception {
            mockMvc.perform(get("/auth/users"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /auth/users → 403 without auth")
        void addUserNoAuth() throws Exception {
            mockMvc.perform(post("/auth/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"discordId\":\"123\",\"discordUsername\":\"test\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE /auth/users/123 → 403 without auth")
        void removeUserNoAuth() throws Exception {
            mockMvc.perform(delete("/auth/users/123"))
                    .andExpect(status().isForbidden());
        }

        // ── Logs ──

        @Test
        @DisplayName("GET /logs/current → 403 without auth")
        void getLogsNoAuth() throws Exception {
            mockMvc.perform(get("/logs/current"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /logs/archive → 403 without auth")
        void archiveLogsNoAuth() throws Exception {
            mockMvc.perform(post("/logs/archive"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /logs/clear → 403 without auth")
        void clearLogsNoAuth() throws Exception {
            mockMvc.perform(post("/logs/clear"))
                    .andExpect(status().isForbidden());
        }

        // ── AH fetch ──

        @Test
        @DisplayName("GET /craftingAH/fetch → 403 without auth")
        void ahFetchNoAuth() throws Exception {
            mockMvc.perform(get("/craftingAH/fetch"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /craftingAH/submit → 403 without auth")
        void ahSubmitNoAuth() throws Exception {
            mockMvc.perform(post("/craftingAH/submit")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("100,50000,10"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Authenticated access ───────────────────────────────────────────

    @Nested
    @DisplayName("Protected endpoints — accessible with valid authentication")
    class AuthenticatedAccess {

        @Test
        @DisplayName("POST /items → not 401/403 with auth (may be 201 or 400)")
        void createItemWithAuth() throws Exception {
            mockMvc.perform(post("/items")
                            .with(user("12345").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":99999,\"name\":\"AuthTest Item\"}"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("GET /auth/users → not 401/403 with auth")
        void getUsersWithAuth() throws Exception {
            mockMvc.perform(get("/auth/users")
                            .with(user("12345").roles("USER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }

        @Test
        @DisplayName("DELETE /items/99999 → not 401/403 with auth (may be 404)")
        void deleteItemWithAuth() throws Exception {
            mockMvc.perform(delete("/items/99999")
                            .with(user("12345").roles("USER")))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotIn(401, 403);
                    });
        }
    }
}
