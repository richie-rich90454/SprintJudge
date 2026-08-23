package com.sprintjudge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class SecurityConfig {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${sprintjudge.admin-emails:}")
    private String adminEmails;

    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        var delegate = new OidcUserService();
        return userRequest -> {
            try {
                OidcUser user = delegate.loadUser(userRequest);
                Set<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities());
                String email = user.getEmail();
                log.info("[OAuth] User authenticated: email={}, authorities={}",
                        email, authorities.stream().map(GrantedAuthority::getAuthority).toList());
                if (email != null && isAdminEmail(email)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    log.info("[OAuth] Email {} is in admin allowlist — ROLE_ADMIN granted", email);
                } else {
                    log.warn("[OAuth] Email {} NOT in admin allowlist [{}] — no ROLE_ADMIN",
                            email, adminEmails);
                }
                return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo());
            } catch (Exception e) {
                log.error("[OAuth] OidcUserService failed during user loading", e);
                throw e;
            }
        };
    }

    private boolean isAdminEmail(String email) {
        for (String allowed : adminEmails.split(",")) {
            if (email.trim().equalsIgnoreCase(allowed.trim())) return true;
        }
        return false;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   OAuth2LoginSuccessHandler successHandler,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService) throws Exception {
        // JSON 401 entry point for API calls — prevents axios from chasing
        // cross-origin 302 redirects into a CSP wall.
        var api401 = new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                org.springframework.http.HttpStatus.UNAUTHORIZED);

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31_536_000))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self' 'unsafe-inline' data:; "
                  + "style-src 'self' 'unsafe-inline'; font-src 'self'; "
                  + "img-src 'self' data: https://login.microsoftonline.com; "
                  + "connect-src 'self' ws: wss: https://login.microsoftonline.com; "
                  + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'"))
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/fonts/**", "/sw.js",
                        "/favicon.ico", "/favicon.svg", "/api/public/**", "/ws", "/oauth2/**",
                        "/login/**", "/api/oauth2/**", "/api/auth/callback/**").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(ui -> ui.oidcUserService(oidcUserService))
                .successHandler(successHandler));

        // CRITICAL: exceptionHandling MUST come AFTER oauth2Login so our API
        // 401 entry point overrides oauth2Login's default redirect entry point.
        http.exceptionHandling(ex -> ex
            .defaultAuthenticationEntryPointFor(api401,
                request -> {
                    String path = request.getRequestURI();
                    return path.startsWith("/api/") && !path.startsWith("/api/public/")
                        && !path.startsWith("/api/oauth2/");
                }));

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
