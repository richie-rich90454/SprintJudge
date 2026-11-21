package com.sprintjudge.config;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class OAuth2ProviderConfigTest {

	private final OAuth2ProviderConfig config = new OAuth2ProviderConfig();

	private static ClientRegistration find(ClientRegistrationRepository repo, String id) {
		return repo.findByRegistrationId(id);
	}

	@Test
	void emptyEnvironmentRegistersNothing() {
		ClientRegistrationRepository repo = config.clientRegistrationRepository(new MockEnvironment());
		for (String id : new String[]{"google", "github", "microsoft", "basis", "oidc", "facebook", "gitlab",
				"linkedin", "okta", "oauth2"}) {
			assertNull(find(repo, id), id);
		}
	}

	@Test
	void allProvidersAreRegisteredWhenConfigured() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRINTJUDGE_GOOGLE_CLIENT_ID", "g");
		env.setProperty("SPRINTJUDGE_GITHUB_CLIENT_ID", "gh");
		env.setProperty("SPRINTJUDGE_MICROSOFT_CLIENT_ID", "m");
		env.setProperty("BASIS_AUTH_ISSUER", "https://auth.example.org/");
		env.setProperty("BASIS_AUTH_CLIENT_ID", "b");
		env.setProperty("SPRINTJUDGE_OIDC_ISSUER", "https://oidc.example.org/");
		env.setProperty("SPRINTJUDGE_OIDC_CLIENT_ID", "o");
		env.setProperty("SPRINTJUDGE_FACEBOOK_CLIENT_ID", "f");
		env.setProperty("SPRINTJUDGE_GITLAB_CLIENT_ID", "gl");
		env.setProperty("SPRINTJUDGE_LINKEDIN_CLIENT_ID", "li");
		env.setProperty("SPRINTJUDGE_OKTA_ISSUER", "https://okta.example.org");
		env.setProperty("SPRINTJUDGE_OKTA_CLIENT_ID", "ok");
		env.setProperty("SPRINTJUDGE_OAUTH_AUTHORIZATION_URI", "https://custom.example.org/authorize");
		env.setProperty("SPRINTJUDGE_OAUTH_CLIENT_ID", "c");
		env.setProperty("SPRINTJUDGE_OAUTH_TOKEN_URI", "https://custom.example.org/token");
		env.setProperty("SPRINTJUDGE_OAUTH_USERINFO_URI", "https://custom.example.org/userinfo");
		ClientRegistrationRepository repo = config.clientRegistrationRepository(env);
		for (String id : new String[]{"google", "github", "microsoft", "basis", "oidc", "facebook", "gitlab",
				"linkedin", "okta", "oauth2"}) {
			assertNotNull(find(repo, id), id);
		}
		assertEquals("https://auth.example.org/oauth/authorize",
				find(repo, "basis").getProviderDetails().getAuthorizationUri());
		assertEquals("https://okta.example.org/oauth2/v1/authorize",
				find(repo, "okta").getProviderDetails().getAuthorizationUri());
	}

	@Test
	void issuerWithoutClientIdIsNotRegistered() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("BASIS_AUTH_ISSUER", "https://auth.example.org");
		env.setProperty("SPRINTJUDGE_OIDC_ISSUER", "https://oidc.example.org");
		env.setProperty("SPRINTJUDGE_OKTA_ISSUER", "https://okta.example.org");
		ClientRegistrationRepository repo = config.clientRegistrationRepository(env);
		assertNull(find(repo, "basis"));
		assertNull(find(repo, "oidc"));
		assertNull(find(repo, "okta"));
	}

	@Test
	void customOauthWithoutRequiredUrisIsNotRegistered() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRINTJUDGE_OAUTH_AUTHORIZATION_URI", "https://custom.example.org/authorize");
		env.setProperty("SPRINTJUDGE_OAUTH_CLIENT_ID", "c");
		ClientRegistrationRepository repo = config.clientRegistrationRepository(env);
		assertNull(find(repo, "oauth2"));
	}

	@Test
	void customOauthWithTokenButNoUserinfoIsNotRegistered() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRINTJUDGE_OAUTH_AUTHORIZATION_URI", "https://custom.example.org/authorize");
		env.setProperty("SPRINTJUDGE_OAUTH_CLIENT_ID", "c");
		env.setProperty("SPRINTJUDGE_OAUTH_TOKEN_URI", "https://custom.example.org/token");
		ClientRegistrationRepository repo = config.clientRegistrationRepository(env);
		assertNull(find(repo, "oauth2"));
	}

	@Test
	void blankClientIdIsIgnored() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRINTJUDGE_GOOGLE_CLIENT_ID", "   ");
		ClientRegistrationRepository repo = config.clientRegistrationRepository(env);
		assertNull(find(repo, "google"));
	}

	@Test
	void oidcWithoutEmailFallsBackToUserRole() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRINTJUDGE_ADMIN_EMAILS", "admin@example.org");
		GrantedAuthoritiesMapper mapper = config.oauthAuthoritiesMapper(env);
		OidcIdToken token = new OidcIdToken("v", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "s"));
		Collection<? extends GrantedAuthority> mapped = mapper
				.mapAuthorities(List.of(new OidcUserAuthority(token), new OAuth2UserAuthority(Map.of("name", "n"))));
		assertTrue(mapped.stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
	}

	@Test
	void unrelatedAuthoritiesAreIgnored() {
		MockEnvironment env = new MockEnvironment();
		GrantedAuthoritiesMapper mapper = config.oauthAuthoritiesMapper(env);
		Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(
				List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_X")));
		assertEquals(2, mapped.size());
	}

	@Test
	void adminEmailGetsAdminRole() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRINTJUDGE_ADMIN_EMAILS", " Teacher@Example.org , other@example.org ");
		GrantedAuthoritiesMapper mapper = config.oauthAuthoritiesMapper(env);
		Collection<? extends GrantedAuthority> mapped = mapper
				.mapAuthorities(List.of(new OAuth2UserAuthority(Map.of("email", "teacher@example.org"))));
		assertTrue(mapped.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
	}

	@Test
	void unknownEmailAndMissingEmailGetUserRole() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRINTJUDGE_ADMIN_EMAILS", "admin@example.org,");
		GrantedAuthoritiesMapper mapper = config.oauthAuthoritiesMapper(env);
		Collection<? extends GrantedAuthority> stranger = mapper
				.mapAuthorities(List.of(new OAuth2UserAuthority(Map.of("email", "student@example.org"))));
		assertTrue(stranger.stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
		Collection<? extends GrantedAuthority> anonymous = mapper.mapAuthorities(List.of());
		assertTrue(anonymous.stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
	}

	@Test
	void oidcEmailFallsBackToUserInfo() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRINTJUDGE_ADMIN_EMAILS", "admin@example.org");
		GrantedAuthoritiesMapper mapper = config.oauthAuthoritiesMapper(env);
		OidcIdToken token = new OidcIdToken("v", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "s"));
		OidcUserAuthority authority = new OidcUserAuthority(token,
				new OidcUserInfo(Map.of("email", "admin@example.org")));
		Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(List.of(authority));
		assertTrue(mapped.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
	}

	@Test
	void oidcIdTokenEmailIsUsedDirectly() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRINTJUDGE_ADMIN_EMAILS", "admin@example.org");
		GrantedAuthoritiesMapper mapper = config.oauthAuthoritiesMapper(env);
		OidcIdToken token = new OidcIdToken("v", Instant.now(), Instant.now().plusSeconds(60),
				Map.of("sub", "s", "email", "admin@example.org"));
		Collection<? extends GrantedAuthority> mapped = mapper.mapAuthorities(List.of(new OidcUserAuthority(token)));
		assertTrue(mapped.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
	}
}
