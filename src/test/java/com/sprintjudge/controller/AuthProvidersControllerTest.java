package com.sprintjudge.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SuppressWarnings("all")
class AuthProvidersControllerTest {

	private static ClientRegistration registration(String id) {
		return ClientRegistration.withRegistrationId(id).clientId("id").clientSecret("secret")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.authorizationUri("https://example.org/authorize").tokenUri("https://example.org/token")
				.userInfoUri("https://example.org/userinfo").userNameAttributeName("sub").clientName("Example").build();
	}

	@Test
	void listsConfiguredProvidersWithAuthorizationUrls() {
		ClientRegistrationRepository repo = new org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository(
				List.of(registration("google"), registration("github")));
		List<Map<String, String>> providers = new AuthProvidersController(repo).providers();
		assertEquals(2, providers.size());
		Map<String, Map<String, String>> byId = new java.util.HashMap<>();
		for (Map<String, String> provider : providers) {
			byId.put(provider.get("id"), provider);
		}
		assertEquals("Example", byId.get("google").get("name"));
		assertEquals("/oauth2/authorization/google", byId.get("google").get("authorizationUrl"));
		assertEquals("/oauth2/authorization/github", byId.get("github").get("authorizationUrl"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void nonRegistrationItemsAreSkipped() {
		ClientRegistrationRepository repo = org.mockito.Mockito.mock(ClientRegistrationRepository.class,
				org.mockito.Mockito.withSettings().extraInterfaces(Iterable.class));
		java.util.List<Object> items = new java.util.ArrayList<>();
		items.add("not a registration");
		org.mockito.Mockito.when(((Iterable<Object>) repo).iterator()).thenReturn(items.iterator());
		assertTrue(new AuthProvidersController(repo).providers().isEmpty());
	}

	@Test
	void nonIterableRepositoryReturnsEmptyList() {
		ClientRegistrationRepository repo = mock(ClientRegistrationRepository.class);
		List<Map<String, String>> providers = new AuthProvidersController(repo).providers();
		assertTrue(providers.isEmpty());
	}
}
