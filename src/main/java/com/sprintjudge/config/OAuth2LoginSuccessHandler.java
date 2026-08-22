package com.sprintjudge.config;

import com.sprintjudge.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Upserts the authenticated Microsoft Entra ID user into the local users
 * table on every successful login, so game sessions can attribute a real
 * host_user_id instead of trusting a client-supplied identifier.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public OAuth2LoginSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (authentication.getPrincipal() instanceof OAuth2User oauth) {
            String email = oauth.<String>getAttribute("email");
            if (email == null || email.isBlank()) email = authName(authentication);
            String name = oauth.<String>getAttribute("name");
            String avatar = oauth.<String>getAttribute("picture");
            userRepository.upsertByEmail(email, name == null ? email : name, avatar);
        }
        response.sendRedirect("/admin/dashboard");
    }

    private String authName(Authentication authentication) {
        return authentication.getName() == null ? "unknown" : authentication.getName();
    }
}
