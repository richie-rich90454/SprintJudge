package com.sprintjudge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OAuth2LoginSuccessHandler successHandler,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // Cookie-based OAuth2 session auth is stateful: CSRF protection stays ON.
            // The SPA reads the XSRF-TOKEN cookie and echoes it as X-XSRF-TOKEN (axios default).
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31_536_000))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    // data: on script-src is required by (a) the SystemJS legacy
                    // bootstrap and (b) the modern-polyfill import.meta.resolve
                    // detection shim — both emit inline data:text/javascript URIs.
                    // login.microsoftonline.com is needed for the OAuth redirect chain.
                    "default-src 'self'; script-src 'self' 'unsafe-inline' data:; "
                  + "style-src 'self' 'unsafe-inline'; font-src 'self'; "
                  + "img-src 'self' data: https://login.microsoftonline.com; "
                  + "connect-src 'self' ws: wss: https://login.microsoftonline.com; "
                  + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'"))
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .exceptionHandling(ex -> ex
                // API calls get a machine-readable 401; page navigations get
                // the OAuth redirect. Without this, axios chases cross-origin
                // redirects into a CSP wall.
                .defaultAuthenticationEntryPointFor(
                    (req, res, ex2) -> {
                        res.setStatus(401);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"type\":\"ERROR\",\"message\":\"Not authenticated\"}");
                    },
                    request -> {
                        var path = request.getRequestURI();
                        return path.startsWith("/api/") && !path.startsWith("/api/public/");
                    }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/fonts/**", "/sw.js",
                        "/favicon.ico", "/favicon.svg", "/api/public/**", "/ws", "/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").authenticated()
                .anyRequest().denyAll())
            .oauth2Login(oauth2 -> oauth2.successHandler(successHandler));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${sprintjudge.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
