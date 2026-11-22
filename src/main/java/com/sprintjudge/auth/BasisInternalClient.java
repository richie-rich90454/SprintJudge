package com.sprintjudge.auth;

/** Sends webhook proof to the basis-auth internal API. Injectable for tests. */
public interface BasisInternalClient {
	void validateWebhook(String resource, String secret, String nonce, String challengeHash);
}
