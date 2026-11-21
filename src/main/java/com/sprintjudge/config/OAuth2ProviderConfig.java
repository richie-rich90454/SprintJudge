package com.sprintjudge.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * OAuth2/OIDC sign-in providers. A provider is registered only when its client
 * id is configured, so an unconfigured deployment behaves exactly as before and
 * the local password login keeps working. OAuth2 users receive the ADMIN role
 * only when their email is listed in SPRINTJUDGE_ADMIN_EMAILS.
 */
@Configuration
public class OAuth2ProviderConfig {

	private static final String REDIRECT = "{baseUrl}/login/oauth2/code/{registrationId}";

	@Bean
	public ClientRegistrationRepository clientRegistrationRepository(Environment environment) {
		List<ClientRegistration> registrations = new ArrayList<>();
		add(environment, registrations, "google", "Google", "SPRINTJUDGE_GOOGLE_CLIENT_ID",
				"SPRINTJUDGE_GOOGLE_CLIENT_SECRET", "https://accounts.google.com/o/oauth2/v2/auth",
				"https://oauth2.googleapis.com/token", "https://www.googleapis.com/oauth2/v3/userinfo",
				"https://www.googleapis.com/oauth2/v3/certs", "https://accounts.google.com", "sub", "openid", "profile",
				"email");
		add(environment, registrations, "github", "GitHub", "SPRINTJUDGE_GITHUB_CLIENT_ID",
				"SPRINTJUDGE_GITHUB_CLIENT_SECRET", "https://github.com/login/oauth/authorize",
				"https://github.com/login/oauth/access_token", "https://api.github.com/user", null, null, "login",
				"read:user", "user:email");
		String tenant = environment.getProperty("SPRINTJUDGE_MICROSOFT_TENANT", "common");
		add(environment, registrations, "microsoft", "Microsoft", "SPRINTJUDGE_MICROSOFT_CLIENT_ID",
				"SPRINTJUDGE_MICROSOFT_CLIENT_SECRET",
				"https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize",
				"https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token",
				"https://graph.microsoft.com/oidc/userinfo",
				"https://login.microsoftonline.com/" + tenant + "/discovery/v2.0/keys",
				"https://login.microsoftonline.com/" + tenant + "/v2.0", "sub", "openid", "profile", "email");
		String basis = prop(environment, "BASIS_AUTH_ISSUER");
		if (basis != null) {
			String issuer = stripSlash(basis);
			add(environment, registrations, "basis", "Basis Auth", "BASIS_AUTH_CLIENT_ID", "BASIS_AUTH_CLIENT_SECRET",
					issuer + "/oauth/authorize", issuer + "/oauth/token", issuer + "/oauth/userinfo",
					issuer + "/oauth/jwks", issuer, "sub", "openid", "profile", "email");
		}
		String oidc = prop(environment, "SPRINTJUDGE_OIDC_ISSUER");
		if (oidc != null) {
			String issuer = stripSlash(oidc);
			add(environment, registrations, "oidc", "OpenID Connect", "SPRINTJUDGE_OIDC_CLIENT_ID",
					"SPRINTJUDGE_OIDC_CLIENT_SECRET", issuer + "/oauth/authorize", issuer + "/oauth/token",
					issuer + "/oauth/userinfo", issuer + "/oauth/jwks", issuer, "sub", "openid", "profile", "email");
		}
		add(environment, registrations, "facebook", "Facebook", "SPRINTJUDGE_FACEBOOK_CLIENT_ID",
				"SPRINTJUDGE_FACEBOOK_CLIENT_SECRET", "https://www.facebook.com/v21.0/dialog/oauth",
				"https://graph.facebook.com/v21.0/oauth/access_token", "https://graph.facebook.com/me", null, null,
				"id", "email", "public_profile");
		add(environment, registrations, "gitlab", "GitLab", "SPRINTJUDGE_GITLAB_CLIENT_ID",
				"SPRINTJUDGE_GITLAB_CLIENT_SECRET", "https://gitlab.com/oauth/authorize",
				"https://gitlab.com/oauth/token", "https://gitlab.com/api/v4/user", null, null, "id", "read_user",
				"openid", "email", "profile");
		add(environment, registrations, "linkedin", "LinkedIn", "SPRINTJUDGE_LINKEDIN_CLIENT_ID",
				"SPRINTJUDGE_LINKEDIN_CLIENT_SECRET", "https://www.linkedin.com/oauth/v2/authorization",
				"https://www.linkedin.com/oauth/v2/accessToken", "https://api.linkedin.com/v2/userinfo", null,
				"https://www.linkedin.com", "sub", "openid", "profile", "email");
		String okta = prop(environment, "SPRINTJUDGE_OKTA_ISSUER");
		if (okta != null) {
			String issuer = stripSlash(okta);
			add(environment, registrations, "okta", "Okta", "SPRINTJUDGE_OKTA_CLIENT_ID",
					"SPRINTJUDGE_OKTA_CLIENT_SECRET", issuer + "/oauth2/v1/authorize", issuer + "/oauth2/v1/token",
					issuer + "/oauth2/v1/userinfo", issuer + "/oauth2/v1/keys", issuer, "sub", "openid", "profile",
					"email");
		}
		String customAuth = prop(environment, "SPRINTJUDGE_OAUTH_AUTHORIZATION_URI");
		if (customAuth != null) {
			add(environment, registrations, "oauth2", "OAuth 2.0", "SPRINTJUDGE_OAUTH_CLIENT_ID",
					"SPRINTJUDGE_OAUTH_CLIENT_SECRET", customAuth, prop(environment, "SPRINTJUDGE_OAUTH_TOKEN_URI"),
					prop(environment, "SPRINTJUDGE_OAUTH_USERINFO_URI"), null, null,
					environment.getProperty("SPRINTJUDGE_OAUTH_USERNAME_ATTRIBUTE", "sub"), "openid", "profile",
					"email");
		}
		if (registrations.isEmpty()) {
			// The repository interface permits no providers; oauth2Login simply
			// has nothing to offer and the password login remains available.
			return new ClientRegistrationRepository() {
				@Override
				public ClientRegistration findByRegistrationId(String registrationId) {
					return null;
				}
			};
		}
		return new InMemoryClientRegistrationRepository(registrations);
	}

	private static void add(Environment env, List<ClientRegistration> out, String id, String name, String idKey,
			String secretKey, String authorizationUri, String tokenUri, String userInfoUri, String jwkSetUri,
			String issuerUri, String userNameAttribute, String... scopes) {
		String clientId = prop(env, idKey);
		if (clientId == null || tokenUri == null || userInfoUri == null)
			return;
		ClientRegistration.Builder builder = ClientRegistration.withRegistrationId(id).clientId(clientId)
				.clientSecret(env.getProperty(secretKey, ""))
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(REDIRECT)
				.authorizationUri(authorizationUri).tokenUri(tokenUri).userInfoUri(userInfoUri)
				.userNameAttributeName(userNameAttribute).clientName(name).scope(scopes);
		if (jwkSetUri != null)
			builder.jwkSetUri(jwkSetUri);
		if (issuerUri != null)
			builder.issuerUri(issuerUri);
		out.add(builder.build());
	}

	private static String stripSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private static String prop(Environment environment, String name) {
		String value = environment.getProperty(name);
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	@Bean
	public GrantedAuthoritiesMapper oauthAuthoritiesMapper(Environment env) {
		List<String> allowed = new ArrayList<>();
		for (String entry : env.getProperty("SPRINTJUDGE_ADMIN_EMAILS", "").split(",")) {
			if (!entry.trim().isEmpty()) {
				allowed.add(entry.trim().toLowerCase());
			}
		}
		final List<String> admins = allowed;
		return new GrantedAuthoritiesMapper() {
			@Override
			public Collection<? extends GrantedAuthority> mapAuthorities(
					Collection<? extends GrantedAuthority> authorities) {
				String email = emailOf(authorities);
				boolean admin = email != null && admins.contains(email.toLowerCase());
				List<GrantedAuthority> mapped = new ArrayList<>(authorities);
				mapped.add(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_USER"));
				return mapped;
			}
		};
	}

	/**
	 * Only verified emails may grant admin. An unverified or secondary email on an
	 * OAuth account is not proof of control of the allowlisted address.
	 */
	private static String emailOf(Collection<? extends GrantedAuthority> authorities) {
		for (GrantedAuthority authority : authorities) {
			if (authority instanceof OidcUserAuthority) {
				OidcUserAuthority oidc = (OidcUserAuthority) authority;
				if (!verified(oidc.getIdToken().getClaim("email_verified"))
						&& (oidc.getUserInfo() == null || !verified(oidc.getUserInfo().getClaim("email_verified"))))
					continue;
				String email = oidc.getIdToken().getEmail();
				if (email == null && oidc.getUserInfo() != null) {
					email = oidc.getUserInfo().getEmail();
				}
				if (email != null)
					return email;
			} else if (authority instanceof OAuth2UserAuthority) {
				Map<String, Object> attributes = ((OAuth2UserAuthority) authority).getAttributes();
				if (!verified(attributes.get("email_verified")) && !verified(attributes.get("verified_email")))
					continue;
				Object email = attributes.get("email");
				if (email != null)
					return String.valueOf(email);
			}
		}
		return null;
	}

	private static boolean verified(Object value) {
		return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
	}
}
