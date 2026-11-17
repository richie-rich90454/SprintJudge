package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-testable surface of {@link SecurityConfig}. The
 * {@code securityFilterChain} request-matcher matrix needs a Spring context and
 * is covered by boot tests, not here.
 */
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
}
