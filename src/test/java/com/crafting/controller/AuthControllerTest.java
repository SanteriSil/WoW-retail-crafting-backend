package com.crafting.controller;

import com.crafting.model.AllowedUser;
import com.crafting.repository.AllowedUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Integration tests for the user-management endpoints in {@link AuthController}.
 * Uses full Spring context with H2 in-memory database.
 *
 * Note: Discord OAuth callback (POST /auth/discord/callback) is NOT tested here
 * because it requires live Discord API interaction. It is covered separately when
 * a mock HTTP client is available (see PLAN.md §4.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AllowedUserRepository allowedUserRepository;

    @BeforeEach
    void setUp() {
        allowedUserRepository.deleteAll();
    }

    // ── List users ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /auth/users")
    class GetUsers {

        @Test
        @DisplayName("returns empty list when no users exist")
        void emptyList() throws Exception {
            mockMvc.perform(get("/auth/users")
                            .with(user("admin").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("returns all allowed users")
        void returnsAllUsers() throws Exception {
            allowedUserRepository.save(new AllowedUser(111L, "alice"));
            allowedUserRepository.save(new AllowedUser(222L, "bob"));

            mockMvc.perform(get("/auth/users")
                            .with(user("admin").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].discordUsername",
                            containsInAnyOrder("alice", "bob")));
        }

        @Test
        @DisplayName("discordId is returned as a string (avoids JS precision loss)")
        void discordIdAsString() throws Exception {
            allowedUserRepository.save(new AllowedUser(148170052171071488L, "silkku"));

            mockMvc.perform(get("/auth/users")
                            .with(user("admin").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].discordId", is("148170052171071488")));
        }
    }

    // ── Add user ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/users")
    class AddUser {

        @Test
        @DisplayName("adds new user → 201")
        void addsUser() throws Exception {
            String json = """
                {"discordId": "123456789", "discordUsername": "newuser"}
                """;

            mockMvc.perform(post("/auth/users")
                            .with(user("admin").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.discordId", is("123456789")))
                    .andExpect(jsonPath("$.discordUsername", is("newuser")))
                    .andExpect(jsonPath("$.createdAt", notNullValue()));

            assertThat(allowedUserRepository.existsById(123456789L)).isTrue();
        }

        @Test
        @DisplayName("duplicate discordId → 409 Conflict")
        void duplicateUser() throws Exception {
            allowedUserRepository.save(new AllowedUser(111L, "existing"));

            String json = """
                {"discordId": "111", "discordUsername": "duplicate"}
                """;

            mockMvc.perform(post("/auth/users")
                            .with(user("admin").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", is("User already exists")));
        }

        @Test
        @DisplayName("non-numeric discordId → 400")
        void nonNumericId() throws Exception {
            String json = """
                {"discordId": "not-a-number", "discordUsername": "bad"}
                """;

            mockMvc.perform(post("/auth/users")
                            .with(user("admin").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("digits")));
        }

        @Test
        @DisplayName("blank discordId → 400")
        void blankId() throws Exception {
            String json = """
                {"discordId": "  ", "discordUsername": "bad"}
                """;

            mockMvc.perform(post("/auth/users")
                            .with(user("admin").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("required")));
        }

        @Test
        @DisplayName("null discordId → 400")
        void nullId() throws Exception {
            String json = """
                {"discordUsername": "bad"}
                """;

            mockMvc.perform(post("/auth/users")
                            .with(user("admin").roles("USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Remove user ────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /auth/users/{discordId}")
    class RemoveUser {

        @Test
        @DisplayName("removes existing user → 204")
        void removesUser() throws Exception {
            allowedUserRepository.save(new AllowedUser(111L, "doomed"));

            mockMvc.perform(delete("/auth/users/111")
                            .with(user("admin").roles("USER")))
                    .andExpect(status().isNoContent());

            assertThat(allowedUserRepository.existsById(111L)).isFalse();
        }

        @Test
        @DisplayName("non-existent user → 404")
        void notFound() throws Exception {
            mockMvc.perform(delete("/auth/users/99999")
                            .with(user("admin").roles("USER")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", is("User not found")));
        }
    }
}
