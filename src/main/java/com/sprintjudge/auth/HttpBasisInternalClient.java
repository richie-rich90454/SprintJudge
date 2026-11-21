package com.sprintjudge.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Default basis-auth internal client using the JDK HTTP client. */
public final class HttpBasisInternalClient implements BasisInternalClient {

	private final URI uri;
	private HttpClient client;

	public HttpBasisInternalClient(String internalUrl) {
		String base = internalUrl.endsWith("/") ? internalUrl.substring(0, internalUrl.length() - 1) : internalUrl;
		this.uri = URI.create(base + "/internal/backchannel/validate");
	}

	@Override
	public synchronized void validateWebhook(String resource, String secret, String nonce, String challengeHash) {
		if (client == null) {
			client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		}
		String body = "{\"nonce\":\"" + escape(nonce) + "\",\"challenge_hash\":\"" + escape(challengeHash) + "\"}";
		try {
			HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
					.header("Authorization", "Bearer " + secret)
					.header("X-Basis-Resource",
							java.net.URLEncoder.encode(resource, java.nio.charset.StandardCharsets.UTF_8))
					.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
			client.send(request, HttpResponse.BodyHandlers.discarding());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (java.io.IOException ignored) {
			// The issuer re-saves the URL if proof is not delivered in time.
		}
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
