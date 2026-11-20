package com.sprintjudge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
	// 1. Re-add .oauth2Login(...) below
	// 2. Inject OAuth2UserService<OidcUserRequest, OidcUser> and pass to it
	// 3. Restore SPRINTJUDGE_MS_* env vars and Azure redirect URI

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService(org.springframework.core.env.Environment environment) {
		if ("changeme".equals(adminPassword)) {
			if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
				throw new IllegalStateException("Refusing to boot in prod with the default admin password; "
						+ "set SPRINTJUDGE_ADMIN_PASSWORD");
			}
			log.warn("Using default admin password -?set sprintjudge.admin.password environment variable!");
		}
		org.springframework.security.core.userdetails.UserDetails admin = User.builder().username(adminUsername)
				.password(passwordEncoder().encode(adminPassword)).roles("ADMIN").build();
		return new InMemoryUserDetailsManager(admin);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
			LoginRateLimitFilter loginRateLimitFilter) throws Exception {
		http.cors(
				new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.CorsConfigurer<HttpSecurity>>() {
					@Override
					public void customize(
							org.springframework.security.config.annotation.web.configurers.CorsConfigurer<HttpSecurity> cors) {
						cors.configurationSource(corsConfigurationSource);
					}
				})
				.addFilterBefore(loginRateLimitFilter,
						org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
				.csrf(new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.CsrfConfigurer<HttpSecurity>>() {
					@Override
					public void customize(
							org.springframework.security.config.annotation.web.configurers.CsrfConfigurer<HttpSecurity> csrf) {
						csrf
								// Public endpoints and the WebSocket handshake carry no session
								// state; every admin API is CSRF-protected (the Axios client
								// echoes XSRF-TOKEN in X-XSRF-TOKEN).
								.ignoringRequestMatchers("/api/public/**", "/ws", "/admin/login", "/admin/logout")
								.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
								// Axios echoes the raw cookie value. Spring Security 7's default
								// XOR handler expects an encoded token and rejects the raw one
								// with 403, breaking every admin POST. Use the plain handler.
								.csrfTokenRequestHandler(
										new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler());
					}
				})
				// Materialize the deferred token on every request so the SPA gets
				// the XSRF-TOKEN cookie before its first POST, not after a 403.
				// CsrfFilter always sets this attribute, so the cast is safe.
				.addFilterAfter(new jakarta.servlet.Filter() {
					@Override
					public void doFilter(jakarta.servlet.ServletRequest request,
							jakarta.servlet.ServletResponse response, jakarta.servlet.FilterChain chain)
							throws java.io.IOException, jakarta.servlet.ServletException {
						((org.springframework.security.web.csrf.CsrfToken) request
								.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName()))
								.getToken();
						chain.doFilter(request, response);
					}
				}, org.springframework.security.web.csrf.CsrfFilter.class).headers(
						new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>>() {
							@Override
							public void customize(
									org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity> headers) {
								headers.frameOptions(
										new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>.FrameOptionsConfig>() {
											@Override
											public void customize(
													org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>.FrameOptionsConfig frameOptions) {
												frameOptions.deny();
											}
										}).httpStrictTransportSecurity(
												new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>.HstsConfig>() {
													@Override
													public void customize(
															org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>.HstsConfig hsts) {
														hsts.includeSubDomains(true).preload(true)
																.maxAgeInSeconds(31_536_000);
													}
												})
										.contentSecurityPolicy(
												new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>.ContentSecurityPolicyConfig>() {
													@Override
													public void customize(
															org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>.ContentSecurityPolicyConfig csp) {
														csp.policyDirectives(
																"default-src 'self'; script-src 'self' blob:; "
																		+ "style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data:; "
																		+ "worker-src 'self' blob:; connect-src 'self' ws: wss: blob:; "
																		+ "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
													}
												})
										.referrerPolicy(
												new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>.ReferrerPolicyConfig>() {
													@Override
													public void customize(
															org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>.ReferrerPolicyConfig referrer) {
														referrer.policy(
																org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN);
													}
												});
							}
						})
				.authorizeHttpRequests(
						new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry>() {
							@Override
							public void customize(
									org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
								auth.requestMatchers("/", "/index.html", "/assets/**", "/fonts/**", "/sw.js",
										"/favicon.ico", "/favicon.svg", "/api/public/**", "/ws", "/admin/login",
										"/actuator/health").permitAll()
										// Public SPA deep links (mirrors SpaWebConfig forward + router.tsx):
										// join QR codes point at /j/<pin> on this origin.
										.requestMatchers("/j/**", "/join", "/play", "/host", "/results", "/solo",
												"/practice", "/explore")
										.permitAll().requestMatchers("/admin/**", "/api/admin/**").authenticated()
										// Ops endpoints stay login-gated (health alone is public).
										.requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/info")
										.authenticated().anyRequest().denyAll();
							}
						})
				.formLogin(
						new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer<HttpSecurity>>() {
							@Override
							public void customize(
									org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer<HttpSecurity> form) {
								form.loginPage("/admin/login").loginProcessingUrl("/admin/login")
										.defaultSuccessUrl("/admin/dashboard", true).permitAll();
							}
						})
				.logout(new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.LogoutConfigurer<HttpSecurity>>() {
					@Override
					public void customize(
							org.springframework.security.config.annotation.web.configurers.LogoutConfigurer<HttpSecurity> logout) {
						logout.logoutUrl("/admin/logout").logoutSuccessUrl("/");
					}
				})
				// API callers get machine-readable 401, not the form-login 302
				// to an HTML page: without this, XHR follows the redirect, the
				// SPA guard sees a 200, and anonymous users land on a dead
				// dashboard instead of bouncing to /admin/login.
				// API callers get machine-readable 401, not the form-login 302
				// to an HTML page: without this, XHR follows the redirect, the
				// SPA guard sees a 200, and anonymous users land on a dead
				// dashboard instead of bouncing to /admin/login.
				.exceptionHandling(
						new org.springframework.security.config.Customizer<org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer<HttpSecurity>>() {
							@Override
							public void customize(
									org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer<HttpSecurity> e) {
								e.authenticationEntryPoint(
										new org.springframework.security.web.AuthenticationEntryPoint() {
											@Override
											public void commence(jakarta.servlet.http.HttpServletRequest request,
													jakarta.servlet.http.HttpServletResponse response,
													org.springframework.security.core.AuthenticationException authException)
													throws java.io.IOException, jakarta.servlet.ServletException {
												if (request.getRequestURI().startsWith("/api/")) {
													response.sendError(
															jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
												} else {
													new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint(
															"/admin/login").commence(request, response, authException);
												}
											}
										});
							}
						});

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${sprintjudge.cors.allowed-origins:}") String allowedOrigins) {
		// Fail-closed: a wildcard origin combined with credentials lets any
		// site ride the admin session cookie. Refuse to boot instead. Trim each
		// entry so "http://a, http://b" does not create a leading-space origin.
		List<String> origins = new java.util.ArrayList<>();
		for (String origin : allowedOrigins.split(",")) {
			String trimmed = origin.trim();
			if ("*".equals(trimmed)) {
				throw new IllegalStateException("Refusing to boot: wildcard CORS origin with allowCredentials=true");
			}
			if (!trimmed.isEmpty()) {
				origins.add(trimmed);
			}
		}
		CorsConfiguration cfg = new CorsConfiguration();
		cfg.setAllowedOriginPatterns(origins);
		cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-XSRF-TOKEN"));
		cfg.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
		src.registerCorsConfiguration("/**", cfg);
		return src;
	}
}
