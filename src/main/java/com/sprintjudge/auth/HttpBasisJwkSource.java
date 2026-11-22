package com.sprintjudge.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Fetches JWKS from the configured basis-auth issuer. */
public final class HttpBasisJwkSource implements BasisJwkSource {

	private final URI uri;
	private HttpClient client;

	public HttpBasisJwkSource(String issuer) {
		String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
		this.uri = URI.create(base + "/oauth/jwks");
		if (!"https".equalsIgnoreCase(this.uri.getScheme()) && !"localhost".equalsIgnoreCase(this.uri.getHost())
				&& !"127.0.0.1".equals(this.uri.getHost())) {
			throw new IllegalArgumentException("BASIS_AUTH_ISSUER must use HTTPS (localhost allowed for tests)");
		}
	}

	@Override
	public synchronized String load() {
		if (client == null) {
			client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
				throw new IllegalStateException("JWKS request failed: " + response.statusCode());
			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("JWKS request interrupted", e);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("JWKS request failed", e);
		}
	}
}
