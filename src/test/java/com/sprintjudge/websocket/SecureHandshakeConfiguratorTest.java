package com.sprintjudge.websocket;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecureHandshakeConfiguratorTest {

    private final SecureHandshakeConfigurator configurator = new SecureHandshakeConfigurator();

    private ServerEndpointConfig secWith(Map<String, Object> props) {
        ServerEndpointConfig sec = mock(ServerEndpointConfig.class);
        when(sec.getUserProperties()).thenReturn(props);
        return sec;
    }

    private HandshakeRequest requestWith(Map<String, List<String>> headers, Principal principal) {
        HandshakeRequest req = mock(HandshakeRequest.class);
        when(req.getHeaders()).thenReturn(headers);
        when(req.getUserPrincipal()).thenReturn(principal);
        when(req.getHttpSession()).thenReturn(null);
        return req;
    }

    private String stagedIp(Map<String, Object> props) {
        return SecureHandshakeConfigurator.consumeStaged(secWith(props)).ip();
    }

    @Test
    void stagesIpFromForwardedFor() {
        Map<String, Object> props = new HashMap<>();
        configurator.modifyHandshake(secWith(props),
                requestWith(Map.of("X-Forwarded-For", List.of("1.2.3.4")), null),
                mock(HandshakeResponse.class));
        assertEquals("1.2.3.4", stagedIp(props));
    }

    @Test
    void forwardedForTakesFirstBeforeComma() {
        Map<String, Object> props = new HashMap<>();
        configurator.modifyHandshake(secWith(props),
                requestWith(Map.of("X-Forwarded-For", List.of(" 1.2.3.4 , 5.6.7.8 ")), null),
                mock(HandshakeResponse.class));
        assertEquals("1.2.3.4", stagedIp(props));
    }

    @Test
    void fallsBackToRealIp() {
        Map<String, Object> props = new HashMap<>();
        configurator.modifyHandshake(secWith(props),
                requestWith(Map.of("X-Real-IP", List.of("9.9.9.9")), null),
                mock(HandshakeResponse.class));
        assertEquals("9.9.9.9", stagedIp(props));
    }

    @Test
    void unresolvedWhenNoHeaders() {
        Map<String, Object> props = new HashMap<>();
        configurator.modifyHandshake(secWith(props),
                requestWith(Map.of(), null),
                mock(HandshakeResponse.class));
        assertEquals("unresolved", stagedIp(props));
    }

    @Test
    void forwardedForPreferredOverRealIp() {
        Map<String, Object> props = new HashMap<>();
        configurator.modifyHandshake(secWith(props),
                requestWith(Map.of("X-Forwarded-For", List.of("1.1.1.1"),
                        "X-Real-IP", List.of("2.2.2.2")), null),
                mock(HandshakeResponse.class));
        assertEquals("1.1.1.1", stagedIp(props));
    }

    @Test
    void consumeStagedRoundTripsIpAndAuth() {
        Map<String, Object> props = new HashMap<>();
        Principal principal = mock(Principal.class);
        ServerEndpointConfig sec = secWith(props);
        configurator.modifyHandshake(sec,
                requestWith(Map.of("X-Forwarded-For", List.of("3.3.3.3")), principal),
                mock(HandshakeResponse.class));
        var staged = SecureHandshakeConfigurator.consumeStaged(sec);
        assertEquals("3.3.3.3", staged.ip());
        assertTrue(staged.authed());
    }

    @Test
    void consumeStagedRemovesEntries() {
        Map<String, Object> props = new HashMap<>();
        ServerEndpointConfig sec = secWith(props);
        configurator.modifyHandshake(sec,
                requestWith(Map.of("X-Forwarded-For", List.of("4.4.4.4")), null),
                mock(HandshakeResponse.class));
        assertEquals("4.4.4.4", SecureHandshakeConfigurator.consumeStaged(sec).ip());
        var second = SecureHandshakeConfigurator.consumeStaged(sec);
        assertEquals("unresolved", second.ip());
        assertFalse(second.authed());
    }

    @Test
    void consumeWithoutTokenReturnsUnresolved() {
        var staged = SecureHandshakeConfigurator.consumeStaged(secWith(new HashMap<>()));
        assertEquals("unresolved", staged.ip());
        assertFalse(staged.authed());
    }

    @Test
    void consumeStagedIPReturnsIpOnly() {
        Map<String, Object> props = new HashMap<>();
        ServerEndpointConfig sec = secWith(props);
        configurator.modifyHandshake(sec,
                requestWith(Map.of("X-Real-IP", List.of("7.7.7.7")), null),
                mock(HandshakeResponse.class));
        assertEquals("7.7.7.7", SecureHandshakeConfigurator.consumeStagedIP(sec));
    }
}
