package com.sprintjudge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${sprintjudge.admin.username:admin}")
    private String adminUsername;

    @Value("${sprintjudge.admin.password:changeme}")
    private String adminPassword;

    // OAuth2 beans (OAuth2LoginSuccessHandler, OidcUserService) remain in the
    // codebase but are not wired into this filter chain. To re-enable:
    //   1. Re-add .oauth2Login(...) below
    //   2. Inject OAuth2UserService<OidcUserRequest, OidcUser> and pass to it
    //   3. Restore SPRINTJUDGE_MS_* env vars and Azure redirect URI

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        if ("changeme".equals(adminPassword)) {
            log.warn("Using default admin password — set sprintjudge.admin.password environment variable!");
        }
        var admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder().encode(adminPassword))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/ws", "/admin/login", "/admin/logout")
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true).preload(true).maxAgeInSeconds(31_536_000))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self' 'unsafe-inline' data:; "
                  + "style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data:; "
                  + "worker-src 'self' blob:; connect-src 'self' ws: wss: blob:; "
                  + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'"))
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/fonts/**", "/sw.js",
                        "/favicon.ico", "/favicon.svg", "/api/public/**", "/ws",
                        "/admin/login", "/actuator/health").permitAll()
                // Public SPA deep links (mirrors SpaWebConfig forward + router.tsx):
                // join QR codes point at /j/<pin> on this origin.
                .requestMatchers("/j/**", "/join", "/play", "/host", "/results",
                        "/solo", "/explore").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").authenticated()
                // Ops endpoints stay login-gated (health alone is public).
                .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/info").authenticated()
                .anyRequest().denyAll())
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/admin/dashboard", true)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/"));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${sprintjudge.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-XSRF-TOKEN"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
