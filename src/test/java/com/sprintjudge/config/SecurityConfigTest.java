package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-testable surface of {@link SecurityConfig}. The
 * {@code securityFilterChain} request-matcher matrix needs a Spring context and
 * is covered by boot tests, not here.
 */
@SuppressWarnings("all")
class SecurityConfigTest {

	private static SecurityConfig config(String user, String password) {
		SecurityConfig cfg = new SecurityConfig();
		ReflectionTestUtils.setField(cfg, "adminUsername", user);
		ReflectionTestUtils.setField(cfg, "adminPassword", password);
		return cfg;
	}

	@Test
	void passwordEncoderRoundTrips() {
		SecurityConfig cfg = config("admin", "secret");
		org.springframework.security.crypto.password.PasswordEncoder encoder = cfg.passwordEncoder();
		assertNotNull(encoder);
		String hash = encoder.encode("secret");
		assertTrue(encoder.matches("secret", hash));
		assertTrue(!encoder.matches("wrong", hash));
	}

	@Test
	void userDetailsContainsAdmin() {
		org.springframework.security.core.userdetails.UserDetailsService service = config("boss", "s3cret")
				.userDetailsService(new org.springframework.mock.env.MockEnvironment());
		org.springframework.security.core.userdetails.UserDetails user = service.loadUserByUsername("boss");
		assertNotNull(user);
		assertEquals("boss", user.getUsername());
		boolean hasAdmin = false;
		for (org.springframework.security.core.GrantedAuthority a : user.getAuthorities()) {
			if (a.getAuthority().equals("ROLE_ADMIN")) {
				hasAdmin = true;
				break;
			}
		}
		assertTrue(hasAdmin);
	}

	@Test
	void defaultPasswordStillBuildsUserOutsideProd() {
		org.springframework.security.core.userdetails.UserDetailsService service = config("admin", "changeme")
				.userDetailsService(new org.springframework.mock.env.MockEnvironment());
		assertNotNull(service.loadUserByUsername("admin"));
	}

	@Test
	void defaultPasswordRefusesToBootInProd() {
		org.springframework.mock.env.MockEnvironment prod = new org.springframework.mock.env.MockEnvironment();
		prod.setActiveProfiles("prod");
		IllegalStateException e = assertThrows(IllegalStateException.class,
				new org.junit.jupiter.api.function.Executable() {
					@Override
					public void execute() {
						config("admin", "changeme").userDetailsService(prod);
					}
				});
		assertTrue(e.getMessage().contains("default admin password"));
	}

	@Test
	void explicitPasswordBootsInProd() {
		org.springframework.mock.env.MockEnvironment prod = new org.springframework.mock.env.MockEnvironment();
		prod.setActiveProfiles("prod");
		assertNotNull(config("admin", "s3cret").userDetailsService(prod).loadUserByUsername("admin"));
	}

	@Test
	void corsSingleOrigin() {
		org.springframework.web.cors.CorsConfigurationSource src = config("admin", "x")
				.corsConfigurationSource("http://localhost:5173");
		assertNotNull(src);
		org.springframework.web.cors.CorsConfiguration cfg = src.getCorsConfiguration(
				new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/admin/quizzes"));
		assertNotNull(cfg);
		assertTrue(cfg.getAllowedOriginPatterns().contains("http://localhost:5173"));
		assertTrue(cfg.getAllowCredentials());
	}

	@Test
	void corsMultipleOriginsSplit() {
		org.springframework.web.cors.CorsConfigurationSource src = config("admin", "x")
				.corsConfigurationSource("https://a.example,https://b.example");
		org.springframework.web.cors.CorsConfiguration cfg = src
				.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/"));
		assertNotNull(cfg);
		assertEquals(2, cfg.getAllowedOriginPatterns().size());
	}

	@Test
	void corsEmptyDefaultAllowsLoopbackOnly() {
		CorsConfigurationSource src = config("admin", "x").corsConfigurationSource("");
		CorsConfiguration cfg = src.getCorsConfiguration(new MockHttpServletRequest("GET", "/"));
		assertNotNull(cfg);
		assertEquals(2, cfg.getAllowedOriginPatterns().size());
		assertTrue(cfg.getAllowedOriginPatterns().contains("http://localhost:*"));
		assertTrue(cfg.getAllowedOriginPatterns().contains("http://127.0.0.1:*"));
		assertFalse(cfg.getAllowedOriginPatterns().contains("*"));
	}

	@Test
	void corsBlankEntriesAreTrimmedAndSkipped() {
		CorsConfigurationSource src = config("admin", "x")
				.corsConfigurationSource("http://a.example, , http://b.example");
		CorsConfiguration cfg = src.getCorsConfiguration(new MockHttpServletRequest("GET", "/"));
		assertEquals(2, cfg.getAllowedOriginPatterns().size());
		assertTrue(cfg.getAllowedOriginPatterns().contains("http://a.example"));
		assertTrue(cfg.getAllowedOriginPatterns().contains("http://b.example"));
	}

	@Test
	void corsMethodsAndHeaders() {
		org.springframework.web.cors.CorsConfigurationSource src = config("admin", "x")
				.corsConfigurationSource("http://localhost:5173");
		org.springframework.web.cors.CorsConfiguration cfg = src
				.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest("GET", "/"));
		assertNotNull(cfg);
		assertTrue(cfg.getAllowedMethods().containsAll(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")));
		assertTrue(cfg.getAllowedHeaders().contains("Authorization"));
		assertTrue(cfg.getAllowedHeaders().contains("X-XSRF-TOKEN"));
	}

	@Test
	void corsWildcardWithCredentialsRefusesToBoot() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
				new org.junit.jupiter.api.function.Executable() {
					@Override
					public void execute() {
						config("admin", "x").corsConfigurationSource("*");
					}
				});
		assertTrue(e.getMessage().contains("wildcard"));
	}

	@Test
	void corsWildcardAmongOriginsRefusesToBoot() {
		assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				config("admin", "x").corsConfigurationSource("https://a.example, *");
			}
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void filterChainCustomizersAndCsrfFilterHandleMissingToken() throws Exception {
		org.springframework.security.config.annotation.web.builders.HttpSecurity http = org.mockito.Mockito.mock(
				org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
				org.mockito.Answers.RETURNS_SELF);
		org.mockito.Mockito.when(http.build()).thenReturn(
				org.mockito.Mockito.mock(org.springframework.security.web.DefaultSecurityFilterChain.class));
		assertNotNull(config("admin", "secret").securityFilterChain(http,
				config("admin", "secret").corsConfigurationSource(""), null, null, null));

		java.util.Collection<org.mockito.invocation.Invocation> calls = org.mockito.Mockito.mockingDetails(http)
				.getInvocations();
		java.util.List<Object> customizers = calls.stream().filter(c -> c.getArguments().length > 0)
				.map(c -> c.getArgument(0)).filter(a -> a instanceof org.springframework.security.config.Customizer)
				.toList();
		jakarta.servlet.Filter csrfMaterializer = calls.stream()
				.filter(c -> "addFilterAfter".equals(c.getMethod().getName()))
				.map(c -> (jakarta.servlet.Filter) c.getArgument(0)).findFirst().orElseThrow();

		// No attribute on CSRF-ignored endpoints must be a no-op, not an NPE.
		final boolean[] chained = {false};
		jakarta.servlet.FilterChain chain = (req, res) -> chained[0] = true;
		csrfMaterializer.doFilter(new MockHttpServletRequest("GET", "/api/public/ping"),
				new org.springframework.mock.web.MockHttpServletResponse(), chain);
		assertTrue(chained[0]);

		jakarta.servlet.http.HttpServletRequest withToken = new MockHttpServletRequest("GET", "/admin/dashboard");
		org.springframework.security.web.csrf.CsrfToken token = org.mockito.Mockito
				.mock(org.springframework.security.web.csrf.CsrfToken.class);
		org.mockito.Mockito.when(token.getToken()).thenReturn("raw-token");
		withToken.setAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName(), token);
		csrfMaterializer.doFilter(withToken, new org.springframework.mock.web.MockHttpServletResponse(), chain);
		org.mockito.Mockito.verify(token).getToken();

		Object headers = org.mockito.Mockito.mock(
				org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.class,
				org.mockito.Answers.RETURNS_SELF);
		((org.springframework.security.config.Customizer<Object>) customizers.get(2)).customize(headers);
		java.util.List<Object> headerCustomizers = org.mockito.Mockito.mockingDetails(headers).getInvocations().stream()
				.filter(c -> c.getArguments().length > 0).map(c -> c.getArgument(0))
				.filter(a -> a instanceof org.springframework.security.config.Customizer).toList();
		((org.springframework.security.config.Customizer<Object>) headerCustomizers.get(0))
				.customize(org.mockito.Mockito.mock(
						org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig.class,
						org.mockito.Answers.RETURNS_SELF));
		((org.springframework.security.config.Customizer<Object>) headerCustomizers.get(1))
				.customize(org.mockito.Mockito.mock(
						org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.HstsConfig.class,
						org.mockito.Answers.RETURNS_SELF));
		((org.springframework.security.config.Customizer<Object>) headerCustomizers.get(2))
				.customize(org.mockito.Mockito.mock(
						org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.ContentSecurityPolicyConfig.class,
						org.mockito.Answers.RETURNS_SELF));
		((org.springframework.security.config.Customizer<Object>) headerCustomizers.get(3))
				.customize(org.mockito.Mockito.mock(
						org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.ReferrerPolicyConfig.class,
						org.mockito.Answers.RETURNS_SELF));

		Object oauth2 = org.mockito.Mockito.mock(
				org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer.class,
				org.mockito.Answers.RETURNS_SELF);
		((org.springframework.security.config.Customizer<Object>) customizers.get(5)).customize(oauth2);
		Object userInfo = org.mockito.Mockito.mockingDetails(oauth2).getInvocations().stream()
				.filter(c -> c.getArguments().length > 0).map(c -> c.getArgument(0))
				.filter(a -> a instanceof org.springframework.security.config.Customizer).findFirst().orElseThrow();
		((org.springframework.security.config.Customizer<Object>) userInfo).customize(org.mockito.Mockito.mock(
				org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer.UserInfoEndpointConfig.class,
				org.mockito.Answers.RETURNS_SELF));

		org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer<?> exceptions = org.mockito.Mockito
				.mock(org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer.class,
						org.mockito.Answers.RETURNS_SELF);
		((org.springframework.security.config.Customizer<Object>) customizers.get(7)).customize(exceptions);
		org.springframework.security.web.AuthenticationEntryPoint entryPoint = org.mockito.Mockito
				.mockingDetails(exceptions).getInvocations().stream()
				.filter(c -> "authenticationEntryPoint".equals(c.getMethod().getName()))
				.map(c -> (org.springframework.security.web.AuthenticationEntryPoint) c.getArgument(0)).findFirst()
				.orElseThrow();
		org.springframework.security.core.AuthenticationException failure = new org.springframework.security.authentication.BadCredentialsException(
				"nope");
		entryPoint.commence(new MockHttpServletRequest("GET", "/api/admin/quizzes"),
				new org.springframework.mock.web.MockHttpServletResponse(), failure);
		entryPoint.commence(new MockHttpServletRequest("GET", "/admin/dashboard"),
				new org.springframework.mock.web.MockHttpServletResponse(), failure);
	}
}
