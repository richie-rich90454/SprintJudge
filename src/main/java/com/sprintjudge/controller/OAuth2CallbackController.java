package com.sprintjudge.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Redirects the user's custom OAuth2 callback path to Spring Security's
 * internal processing path.
 *
 * <p>Spring Security's OAuth2LoginAuthenticationFilter hardcodes its callback
 * matcher to /login/oauth2/code/{registrationId}. Custom redirect URIs (like
 * /api/oauth2/mscallback registered in Azure Portal) receive the auth code,
 * but Spring Security won't process them unless they arrive at its own path.
 *
 * <p>This controller bridges the gap: it receives the code from Microsoft,
 * then redirects the browser to Spring Security's expected path with the same
 * query parameters. Spring Security then processes it normally.
 */
@RestController
public class OAuth2CallbackController {

    @GetMapping("/api/oauth2/mscallback")
    public void microsoftCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            HttpServletResponse response) throws IOException {
        var sb = new StringBuilder("/login/oauth2/code/microsoft?code=")
                .append(java.net.URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8));
        if (state != null) {
            sb.append("&state=").append(java.net.URLEncoder.encode(state,
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        response.sendRedirect(sb.toString());
    }
}
