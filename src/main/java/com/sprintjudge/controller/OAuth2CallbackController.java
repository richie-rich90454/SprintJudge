package com.sprintjudge.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Redirects the user's custom OAuth2 callback path to Spring Security's
 * internal processing path (/login/oauth2/code/microsoft).
 */
@RestController
public class OAuth2CallbackController {

    private static final String INTERNAL_PATH = "/login/oauth2/code/microsoft";

    @GetMapping({"/api/auth/callback/microsoft-entra-id"})
    public void microsoftCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            HttpServletResponse response) throws IOException {
        var sb = new StringBuilder(INTERNAL_PATH).append("?code=")
                .append(java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8));
        if (state != null && !state.isEmpty()) {
            sb.append("&state=").append(java.net.URLEncoder.encode(state,
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        response.sendRedirect(sb.toString());
    }
}
