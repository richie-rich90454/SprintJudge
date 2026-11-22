package com.sprintjudge.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.sprintjudge.auth.BasisInternalClient;
import com.sprintjudge.auth.BasisSessionService;
import com.sprintjudge.auth.HttpBasisInternalClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
class BasisAuthConfigTest {

	private final BasisAuthConfig config = new BasisAuthConfig();

	@Test
	void blankInternalUrlUsesNoOpClient() {
		BasisInternalClient client = config.basisInternalClient(new MockEnvironment());
		client.validateWebhook("urn", "secret", "nonce", "hash");
		assertNotNull(client);
	}

	@Test
	void configuredInternalUrlUsesHttpClient() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("BASIS_AUTH_INTERNAL_URL", "http://127.0.0.1:1/");
		assertTrue(config.basisInternalClient(env) instanceof HttpBasisInternalClient);
	}

	@Test
	void sessionListenerIsRegistered() {
		assertNotNull(config.basisSessionListener(new BasisSessionService()));
	}
}
