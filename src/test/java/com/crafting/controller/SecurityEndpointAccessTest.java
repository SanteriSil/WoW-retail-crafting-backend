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
    }
}
