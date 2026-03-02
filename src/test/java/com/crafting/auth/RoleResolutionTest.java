package com.crafting.auth;

import com.crafting.cache.CachedResult;
import com.crafting.config.OwnerConfig;
import com.crafting.model.AllowedUser;
import com.crafting.repository.AllowedUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthFilter} role resolution logic (PLAN.md §10.1).
 *
 * Tests the role resolution order:
 *   1. discordId == ownerConfig → ROLE_OWNER (zero DB cost)
 *   2. DB lookup via cache → ROLE_ADMIN or ROLE_ALLOWED_USER
 *   3. Unknown user → no authentication set (null)
 */
class RoleResolutionTest {

    private static final long OWNER_ID = 148170052171071488L;
    private static final long ADMIN_ID = 222222222L;
    private static final long USER_ID  = 333333333L;
    private static final long UNKNOWN_ID = 999999999L;
    private static final String SECRET = "test-secret-key-for-role-resolution-tests-at-least-32-bytes";

    private JwtService jwtService;
    private JwtAuthFilter filter;
    private AllowedUserRepository allowedUserRepository;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);

        OwnerConfig ownerConfig = mock(OwnerConfig.class);
        when(ownerConfig.getDiscordId()).thenReturn(OWNER_ID);

        allowedUserRepository = mock(AllowedUserRepository.class);
        AllowedUser adminUser = new AllowedUser(ADMIN_ID, "admin-user");
        adminUser.setRole(Role.ADMIN);
        AllowedUser allowedUser = new AllowedUser(USER_ID, "regular-user");
        allowedUser.setRole(Role.ALLOWED_USER);
        when(allowedUserRepository.findAll()).thenReturn(List.of(adminUser, allowedUser));

        // Real CachedResult with short TTL so each test gets a fresh load
        CachedResult<Map<Long, Role>> roleLookupCache = new CachedResult<>(Duration.ofSeconds(5));

        filter = new JwtAuthFilter(jwtService, ownerConfig, allowedUserRepository, roleLookupCache);
    }

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(long discordId, Role role) {
        String token = jwtService.generateToken(discordId, "user-" + discordId, role);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    // ── Owner resolution ───────────────────────────────────────────────

    @Nested
    @DisplayName("Owner resolution — from config, no DB hit")
    class OwnerResolution {

        @Test
        @DisplayName("Owner discord ID → ROLE_OWNER granted")
        void ownerGetsOwnerRole() throws Exception {
            MockHttpServletRequest request = requestWithToken(OWNER_ID, Role.OWNER);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getAuthorities())
                    .extracting(a -> a.getAuthority())
                    .containsExactly("ROLE_OWNER");
        }

        @Test
        @DisplayName("Owner resolution does not query the DB")
        void ownerDoesNotHitDb() throws Exception {
            MockHttpServletRequest request = requestWithToken(OWNER_ID, Role.OWNER);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            verify(allowedUserRepository, never()).findAll();
        }
    }

    // ── ADMIN resolution ───────────────────────────────────────────────

    @Nested
    @DisplayName("ADMIN resolution — from DB via cache")
    class AdminResolution {

        @Test
        @DisplayName("ADMIN discord ID → ROLE_ADMIN granted")
        void adminGetsAdminRole() throws Exception {
            MockHttpServletRequest request = requestWithToken(ADMIN_ID, Role.ADMIN);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getAuthorities())
                    .extracting(a -> a.getAuthority())
                    .containsExactly("ROLE_ADMIN");
        }
    }

    // ── ALLOWED_USER resolution ────────────────────────────────────────

    @Nested
    @DisplayName("ALLOWED_USER resolution — from DB via cache")
    class AllowedUserResolution {

        @Test
        @DisplayName("ALLOWED_USER discord ID → ROLE_ALLOWED_USER granted")
        void allowedUserGetsCorrectRole() throws Exception {
            MockHttpServletRequest request = requestWithToken(USER_ID, Role.ALLOWED_USER);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getAuthorities())
                    .extracting(a -> a.getAuthority())
                    .containsExactly("ROLE_ALLOWED_USER");
        }
    }

    // ── Unknown user resolution ────────────────────────────────────────

    @Nested
    @DisplayName("Unknown user — no authentication set")
    class UnknownUserResolution {

        @Test
        @DisplayName("Unknown discord ID → no authentication set (null)")
        void unknownUserGetsNoAuth() throws Exception {
            MockHttpServletRequest request = requestWithToken(UNKNOWN_ID, Role.ALLOWED_USER);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
        }
    }

    // ── Cache behaviour ────────────────────────────────────────────────

    @Nested
    @DisplayName("Cache behaviour — DB loaded at most once per TTL window")
    class CacheBehaviour {

        @Test
        @DisplayName("Two requests within TTL window → DB queried only once")
        void dbLoadedOncePerCacheWindow() throws Exception {
            MockHttpServletRequest req1 = requestWithToken(USER_ID, Role.ALLOWED_USER);
            MockHttpServletRequest req2 = requestWithToken(ADMIN_ID, Role.ADMIN);

            filter.doFilter(req1, new MockHttpServletResponse(), new MockFilterChain());
            SecurityContextHolder.clearContext();
            filter.doFilter(req2, new MockHttpServletResponse(), new MockFilterChain());

            // Only one DB query despite two requests (both served from cache)
            verify(allowedUserRepository, times(1)).findAll();
        }
    }
}
