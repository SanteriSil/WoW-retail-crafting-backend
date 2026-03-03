package com.crafting.config;

import com.crafting.auth.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .authorizeHttpRequests(authorize -> authorize

                // ── Public (no auth required) ──────────────────────────────
                .requestMatchers("/auth/discord/**", "/auth/dev/**",
                                 "/health", "/actuator/**", "/h2-console/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/items", "/items/ids", "/items/ordered").permitAll()
                .requestMatchers(HttpMethod.GET, "/professions").permitAll()
                .requestMatchers(HttpMethod.GET, "/expansions").permitAll()

                // ── ALLOWED_USER or higher: read recipes, profit, export ───
                .requestMatchers(HttpMethod.GET, "/recipes", "/recipes/**").hasAnyRole("ALLOWED_USER", "ADMIN", "OWNER")
                .requestMatchers(HttpMethod.GET, "/export/**").hasAnyRole("ALLOWED_USER", "ADMIN", "OWNER")

                // ── ALLOWED_USER or higher: character management ──────────
                .requestMatchers("/characters", "/characters/**").hasAnyRole("ALLOWED_USER", "ADMIN", "OWNER")

                // ── OWNER only: promote / demote admins ───────────────────
                .requestMatchers("/auth/users/*/promote", "/auth/users/*/demote").hasRole("OWNER")

                // ── ADMIN or higher: everything else that requires login ───
                .requestMatchers(HttpMethod.GET, "/auth/me").hasAnyRole("ALLOWED_USER", "ADMIN", "OWNER")
                .requestMatchers("/auth/users/**").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers(HttpMethod.POST, "/items/**").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers(HttpMethod.PUT,  "/items/**").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers(HttpMethod.DELETE, "/items/**").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers(HttpMethod.POST, "/recipes/**").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers(HttpMethod.PUT,  "/recipes/**").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers(HttpMethod.DELETE, "/recipes/**").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers("/craftingAH/**").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers("/logs/**").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers("/scraper/**").hasAnyRole("ADMIN", "OWNER")

                .anyRequest().authenticated()
            )
            // Return 401 (not 403) for missing/invalid authentication so the
            // frontend's auto-logout handler in api.ts fires correctly.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
