package com.sprintjudge.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public list of configured OAuth2 sign-in providers for the login screen. */
@RestController
@RequestMapping("/api/public/auth")
public class AuthProvidersController {

	private final ClientRegistrationRepository registrations;

	public AuthProvidersController(ClientRegistrationRepository registrations) {
		this.registrations = registrations;
	}

	@GetMapping("/providers")
	public List<Map<String, String>> providers() {
		List<Map<String, String>> out = new ArrayList<>();
		if (!(registrations instanceof Iterable)) {
			return out;
		}
		for (Object item : (Iterable<?>) registrations) {
			if (!(item instanceof ClientRegistration)) {
				continue;
			}
			ClientRegistration registration = (ClientRegistration) item;
			Map<String, String> entry = new LinkedHashMap<>();
			entry.put("id", registration.getRegistrationId());
			entry.put("name", registration.getClientName());
			entry.put("authorizationUrl", "/oauth2/authorization/" + registration.getRegistrationId());
			out.add(entry);
		}
		return out;
	}
}
