package com.sprintjudge.config;

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.sprintjudge.auth.BasisInternalClient;
import com.sprintjudge.auth.BasisSessionService;
import com.sprintjudge.auth.HttpBasisInternalClient;

/** Wiring for the optional basis-auth OIDC integration. */
@Configuration
public class BasisAuthConfig {

	@Bean
	public BasisInternalClient basisInternalClient(Environment environment) {
		String internal = environment.getProperty("BASIS_AUTH_INTERNAL_URL", "");
		if (internal.isBlank()) {
			return new BasisInternalClient() {
				@Override
				public void validateWebhook(String resource, String secret, String nonce, String challengeHash) {
					// No internal API configured: the issuer will keep the previous URL.
				}
			};
		}
		return new HttpBasisInternalClient(internal);
	}

	@Bean
	public ServletListenerRegistrationBean<BasisSessionService> basisSessionListener(BasisSessionService service) {
		return new ServletListenerRegistrationBean<>(service);
	}
}
