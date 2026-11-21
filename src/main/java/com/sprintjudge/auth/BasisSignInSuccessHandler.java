package com.sprintjudge.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Registers the app session under the provider subject before redirecting. */
@Component
public class BasisSignInSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final BasisSessionService sessions;

	public BasisSignInSuccessHandler(BasisSessionService sessions) {
		this.sessions = sessions;
	}

	@jakarta.annotation.PostConstruct
	void configure() {
		setDefaultTargetUrl("/admin/dashboard");
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, jakarta.servlet.ServletException {
		Object principal = authentication.getPrincipal();
		String subject = null;
		if (principal instanceof OidcUser) {
			subject = ((OidcUser) principal).getSubject();
		} else if (principal instanceof OAuth2User) {
			Object value = ((OAuth2User) principal).getAttributes().get("sub");
			if (value == null)
				value = ((OAuth2User) principal).getAttributes().get("login");
			if (value != null)
				subject = String.valueOf(value);
		}
		HttpSession session = request.getSession();
		sessions.register(session.getId(), subject);
		super.onAuthenticationSuccess(request, response, authentication);
	}
}
