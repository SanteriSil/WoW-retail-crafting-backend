package com.crafting.controller;

import com.crafting.model.AccessRequest;
import com.crafting.model.AccessRequestStatus;
import com.crafting.model.AllowedUser;
import com.crafting.repository.AccessRequestRepository;
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
 * Integration tests for {@link AccessRequestController}.
 * Uses full Spring context with H2 in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessRequestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessRequestRepository accessRequestRepository;
    @Autowired private AllowedUserRepository allowedUserRepository;

    @BeforeEach
    void setUp() {
        accessRequestRepository.deleteAll();
        allowedUserRepository.deleteAll();
    }

    // ── Submit access request (public) ─────────────────────────────────

    @Nested
    @DisplayName("POST /auth/access-requests")
    class SubmitRequest {

        @Test
        @DisplayName("creates a pending access request → 201")
        void createsRequest() throws Exception {
            String json = """
                {"discordId": "444555666", "discordUsername": "newplayer"}
                """;

            mockMvc.perform(post("/auth/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message", containsString("submitted")));

            assertThat(accessRequestRepository.findAll()).hasSize(1);
            assertThat(accessRequestRepository.findAll().get(0).getStatus())
                    .isEqualTo(AccessRequestStatus.PENDING);
        }

        @Test
        @DisplayName("no auth required → accessible anonymously")
        void publicEndpoint() throws Exception {
            String json = """
                {"discordId": "111222333", "discordUsername": "anon"}
                """;

            mockMvc.perform(post("/auth/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("duplicate pending request → 409 Conflict")
        void duplicatePending() throws Exception {
            accessRequestRepository.save(new AccessRequest(444L, "existing"));

            String json = """
                {"discordId": "444", "discordUsername": "existing"}
                """;

            mockMvc.perform(post("/auth/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", containsString("pending")));
        }

        @Test
        @DisplayName("user who already has access → 409 Conflict")
        void alreadyHasAccess() throws Exception {
            allowedUserRepository.save(new AllowedUser(555L, "allowed"));

            String json = """
                {"discordId": "555", "discordUsername": "allowed"}
                """;

            mockMvc.perform(post("/auth/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error", containsString("already have access")));
        }

        @Test
        @DisplayName("blank discordId → 400")
        void blankId() throws Exception {
            String json = """
                {"discordId": "  ", "discordUsername": "bad"}
                """;

            mockMvc.perform(post("/auth/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("non-numeric discordId → 400")
        void nonNumericId() throws Exception {
            String json = """
                {"discordId": "abc", "discordUsername": "bad"}
                """;

            mockMvc.perform(post("/auth/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("digits")));
        }

        @Test
        @DisplayName("missing discordUsername → 400")
        void missingUsername() throws Exception {
            String json = """
                {"discordId": "111222333"}
                """;

            mockMvc.perform(post("/auth/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("discordUsername")));
        }

        @Test
        @DisplayName("previously denied request can be resubmitted")
        void resubmitAfterDenied() throws Exception {
            AccessRequest denied = new AccessRequest(777L, "denied-user");
            denied.setStatus(AccessRequestStatus.DENIED);
            accessRequestRepository.save(denied);

            String json = """
                {"discordId": "777", "discordUsername": "denied-user"}
                """;

            mockMvc.perform(post("/auth/access-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());

            // Old denied request should be replaced
            var requests = accessRequestRepository.findByStatus(AccessRequestStatus.PENDING);
            assertThat(requests).hasSize(1);
            assertThat(requests.get(0).getDiscordId()).isEqualTo(777L);
        }
    }

    // ── List pending requests (ADMIN+) ─────────────────────────────────

    @Nested
    @DisplayName("GET /auth/access-requests")
    class ListRequests {

        @Test
        @DisplayName("returns pending requests for ADMIN")
        void returnsPending() throws Exception {
            accessRequestRepository.save(new AccessRequest(111L, "user-a"));
            accessRequestRepository.save(new AccessRequest(222L, "user-b"));

            mockMvc.perform(get("/auth/access-requests")
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].discordUsername",
                            containsInAnyOrder("user-a", "user-b")))
                    .andExpect(jsonPath("$[*].status",
                            everyItem(is("PENDING"))));
        }

        @Test
        @DisplayName("returns empty list when no pending requests")
        void emptyList() throws Exception {
            mockMvc.perform(get("/auth/access-requests")
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("unauthenticated → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/auth/access-requests"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ALLOWED_USER → 403")
        void forbiddenForAllowedUser() throws Exception {
            mockMvc.perform(get("/auth/access-requests")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Approve request (ADMIN+) ───────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/access-requests/{id}/approve")
    class ApproveRequest {

        @Test
        @DisplayName("approves pending request → 200, creates AllowedUser")
        void approvesPending() throws Exception {
            AccessRequest req = accessRequestRepository.save(new AccessRequest(333L, "approved-user"));

            mockMvc.perform(post("/auth/access-requests/" + req.getId() + "/approve")
                            .with(user("12345").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("APPROVED")));

            // AllowedUser should be created
            assertThat(allowedUserRepository.existsById(333L)).isTrue();
            assertThat(allowedUserRepository.findById(333L).get().getDiscordUsername())
                    .isEqualTo("approved-user");

            // Request status should be updated
            AccessRequest updated = accessRequestRepository.findById(req.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
            assertThat(updated.getReviewedBy()).isEqualTo(12345L);
            assertThat(updated.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("non-existent request → 404")
        void notFound() throws Exception {
            mockMvc.perform(post("/auth/access-requests/99999/approve")
                            .with(user("12345").roles("ADMIN")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", containsString("not found")));
        }

        @Test
        @DisplayName("already-approved request → 400")
        void alreadyApproved() throws Exception {
            AccessRequest req = new AccessRequest(444L, "already-approved");
            req.setStatus(AccessRequestStatus.APPROVED);
            req = accessRequestRepository.save(req);

            mockMvc.perform(post("/auth/access-requests/" + req.getId() + "/approve")
                            .with(user("12345").roles("ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("not pending")));
        }

        @Test
        @DisplayName("ALLOWED_USER cannot approve → 403")
        void forbiddenForAllowedUser() throws Exception {
            AccessRequest req = accessRequestRepository.save(new AccessRequest(555L, "victim"));

            mockMvc.perform(post("/auth/access-requests/" + req.getId() + "/approve")
                            .with(user("99999").roles("ALLOWED_USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/auth/access-requests/1/approve"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── Deny request (ADMIN+) ──────────────────────────────────────────

    @Nested
    @DisplayName("POST /auth/access-requests/{id}/deny")
    class DenyRequest {

        @Test
        @DisplayName("denies pending request → 200")
        void deniesPending() throws Exception {
            AccessRequest req = accessRequestRepository.save(new AccessRequest(666L, "denied-user"));

            mockMvc.perform(post("/auth/access-requests/" + req.getId() + "/deny")
                            .with(user("12345").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("DENIED")));

            // No AllowedUser should be created
            assertThat(allowedUserRepository.existsById(666L)).isFalse();

            // Request status should be updated
            AccessRequest updated = accessRequestRepository.findById(req.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AccessRequestStatus.DENIED);
            assertThat(updated.getReviewedBy()).isEqualTo(12345L);
        }

        @Test
        @DisplayName("non-existent request → 404")
        void notFound() throws Exception {
            mockMvc.perform(post("/auth/access-requests/99999/deny")
                            .with(user("12345").roles("ADMIN")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("already-denied request → 400")
        void alreadyDenied() throws Exception {
            AccessRequest req = new AccessRequest(777L, "already-denied");
            req.setStatus(AccessRequestStatus.DENIED);
            req = accessRequestRepository.save(req);

            mockMvc.perform(post("/auth/access-requests/" + req.getId() + "/deny")
                            .with(user("12345").roles("ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("not pending")));
        }

        @Test
        @DisplayName("OWNER can also deny → 200")
        void ownerCanDeny() throws Exception {
            AccessRequest req = accessRequestRepository.save(new AccessRequest(888L, "pending-user"));

            mockMvc.perform(post("/auth/access-requests/" + req.getId() + "/deny")
                            .with(user("12345").roles("OWNER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("DENIED")));
        }
    }
}
