package com.sprintjudge.websocket;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecureHandshakeExtraTest {

    private final SecureHandshakeConfigurator configurator = new SecureHandshakeConfigurator();

    @AfterEach
    void resetContext() {
        configurator.holdApplicationContext(null);
    }

    private ServerEndpointConfig secWith(Map<String, Object> props) {
        ServerEndpointConfig sec = mock(ServerEndpointConfig.class);
        when(sec.getUserProperties()).thenReturn(props);
        return sec;
    }

    private HandshakeRequest requestWith(Map<String, List<String>> headers, Principal principal, Object session) {
        HandshakeRequest req = mock(HandshakeRequest.class);
        when(req.getHeaders()).thenReturn(headers);
        when(req.getUserPrincipal()).thenReturn(principal);
        try {
            when(req.getHttpSession()).thenReturn(session);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return req;
    }

    private void handshake(Map<String, Object> props, Map<String, List<String>> headers,
                           Principal principal, Object session) {
        configurator.modifyHandshake(secWith(props),
                requestWith(headers, principal, session), mock(HandshakeResponse.class));
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Object> stagedMap() throws Exception {
        Field f = SecureHandshakeConfigurator.class.getDeclaredField("staged");
        f.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) f.get(null);
    }

    private Object staged(String ip, boolean authed, long nanos) throws Exception {
        Class<?> c = Class.forName("com.sprintjudge.websocket.SecureHandshakeConfigurator$Staged");
        Constructor<?> ctor = c.getDeclaredConstructor(String.class, boolean.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(ip, authed, nanos);
    }

    @Test
    void endpointInstanceWithoutContextThrows() {
        configurator.holdApplicationContext(null);
        assertThrows(IllegalStateException.class,
                () -> configurator.getEndpointInstance(GameWebSocket.class));
    }

    @Test
    void endpointInstanceResolvesBean() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        GameWebSocket bean = mock(GameWebSocket.class);
        when(ctx.getBean(GameWebSocket.class)).thenReturn(bean);
        configurator.holdApplicationContext(ctx);
        assertEquals(bean, configurator.getEndpointInstance(GameWebSocket.class));
    }

    @Test
    void realIpFirstSegmentWhenCommaList() {
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of("X-Real-IP", List.of("1.1.1.1, 2.2.2.2")), null, null);
        assertEquals("1.1.1.1", SecureHandshakeConfigurator.consumeStaged(secWith(props)).ip());
    }

    @Test
    void forwardedForUsesLastHeaderValue() {
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of("X-Forwarded-For", List.of("9.9.9.9", "5.6.7.8, 6.6.6.6")), null, null);
        assertEquals("6.6.6.6", SecureHandshakeConfigurator.consumeStaged(secWith(props)).ip());
    }

    @Test
    void forwardedForSingleValueNoComma() {
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of("X-Forwarded-For", List.of("8.8.8.8")), null, null);
        assertEquals("8.8.8.8", SecureHandshakeConfigurator.consumeStaged(secWith(props)).ip());
    }

    @Test
    void sessionSecurityContextAuthenticated() {
        HttpSession http = mock(HttpSession.class);
        SecurityContext security = mock(SecurityContext.class);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(security.getAuthentication()).thenReturn(auth);
        when(http.getAttribute("SPRING_SECURITY_CONTEXT")).thenReturn(security);
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of(), null, http);
        assertTrue(SecureHandshakeConfigurator.consumeStaged(secWith(props)).authed());
    }

    @Test
    void sessionAnonymousIsNotAuthenticated() {
        HttpSession http = mock(HttpSession.class);
        SecurityContext security = mock(SecurityContext.class);
        var anon = new AnonymousAuthenticationToken("k", "anon",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        when(security.getAuthentication()).thenReturn(anon);
        when(http.getAttribute("SPRING_SECURITY_CONTEXT")).thenReturn(security);
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of(), null, http);
        assertFalse(SecureHandshakeConfigurator.consumeStaged(secWith(props)).authed());
    }

    @Test
    void sessionNullAuthenticationIsNotAuthenticated() {
        HttpSession http = mock(HttpSession.class);
        SecurityContext security = mock(SecurityContext.class);
        when(security.getAuthentication()).thenReturn(null);
        when(http.getAttribute("SPRING_SECURITY_CONTEXT")).thenReturn(security);
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of(), null, http);
        assertFalse(SecureHandshakeConfigurator.consumeStaged(secWith(props)).authed());
    }

    @Test
    void nonHttpSessionIsNotAuthenticated() {
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of(), null, "not-a-session");
        assertFalse(SecureHandshakeConfigurator.consumeStaged(secWith(props)).authed());
    }

    @Test
    void httpSessionThrowingIsNotAuthenticated() {
        HandshakeRequest req = mock(HandshakeRequest.class);
        when(req.getHeaders()).thenReturn(Map.of());
        when(req.getUserPrincipal()).thenReturn(null);
        when(req.getHttpSession()).thenThrow(new RuntimeException("no session"));
        Map<String, Object> props = new HashMap<>();
        configurator.modifyHandshake(secWith(props), req, mock(HandshakeResponse.class));
        assertFalse(SecureHandshakeConfigurator.consumeStaged(secWith(props)).authed());
    }

    @Test
    void wrongContextAttributeTypeIsNotAuthenticated() {
        HttpSession http = mock(HttpSession.class);
        when(http.getAttribute("SPRING_SECURITY_CONTEXT")).thenReturn("garbage");
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of(), null, http);
        assertFalse(SecureHandshakeConfigurator.consumeStaged(secWith(props)).authed());
    }

    @Test
    void evictStaleRemovesOnlyExpired() throws Exception {
        Method evict = SecureHandshakeConfigurator.class.getDeclaredMethod("evictStale");
        evict.setAccessible(true);
        var map = stagedMap();
        map.clear();
        try {
            map.put("fresh", staged("1.1.1.1", true, System.nanoTime()));
            map.put("old", staged("2.2.2.2", false, System.nanoTime() - java.util.concurrent.TimeUnit.MINUTES.toNanos(6)));
            evict.invoke(null);
            assertTrue(map.containsKey("fresh"));
            assertFalse(map.containsKey("old"));
        } finally {
            map.clear();
        }
    }

    @Test
    void fullStagingTriggersEvictionCap() throws Exception {
        var map = stagedMap();
        map.clear();
        try {
            for (int i = 0; i < 10_000; i++) {
                map.put("k" + i, staged("10.0.0.1", false, System.nanoTime()));
            }
            map.put("stale", staged("9.9.9.9", false, System.nanoTime() - java.util.concurrent.TimeUnit.MINUTES.toNanos(6)));
            Map<String, Object> props = new HashMap<>();
            handshake(props, Map.of("X-Forwarded-For", List.of("7.7.7.7")), null, null);
            assertFalse(map.containsKey("stale"));
            assertEquals("7.7.7.7", SecureHandshakeConfigurator.consumeStaged(secWith(props)).ip());
        } finally {
            map.clear();
        }
    }

    @Test
    void consumeStagedIpTwiceSecondUnresolved() {
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of("X-Real-IP", List.of("7.7.7.7")), null, null);
        ServerEndpointConfig sec = secWith(props);
        assertEquals("7.7.7.7", SecureHandshakeConfigurator.consumeStagedIP(sec));
        assertEquals("unresolved", SecureHandshakeConfigurator.consumeStagedIP(sec));
    }

    @Test
    void handshakeStoresTokenString() {
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of(), null, null);
        Object token = props.get(SecureHandshakeConfigurator.REMOTE_ADDR);
        assertTrue(token instanceof String);
        assertFalse(((String) token).isBlank());
    }

    @Test
    void unknownTokenIsUnresolved() {
        Map<String, Object> props = new HashMap<>();
        props.put(SecureHandshakeConfigurator.REMOTE_ADDR, "no-such-token");
        var staged = SecureHandshakeConfigurator.consumeStaged(secWith(props));
        assertEquals("unresolved", staged.ip());
        assertFalse(staged.authed());
    }

    @Test
    void consumeNullIpYieldsUnresolved() throws Exception {
        var map = stagedMap();
        map.clear();
        try {
            Map<String, Object> props = new HashMap<>();
            props.put(SecureHandshakeConfigurator.REMOTE_ADDR, "null-ip-token");
            map.put("null-ip-token", staged(null, true, System.nanoTime()));
            var got = SecureHandshakeConfigurator.consumeStaged(secWith(props));
            assertEquals("unresolved", got.ip());
            assertTrue(got.authed());
        } finally {
            map.clear();
        }
    }

    @Test
    void nonAnonymousUnauthenticatedIsNotAuthenticated() {
        HttpSession http = mock(HttpSession.class);
        SecurityContext security = mock(SecurityContext.class);
        var auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(security.getAuthentication()).thenReturn(auth);
        when(http.getAttribute("SPRING_SECURITY_CONTEXT")).thenReturn(security);
        Map<String, Object> props = new HashMap<>();
        handshake(props, Map.of(), null, http);
        assertFalse(SecureHandshakeConfigurator.consumeStaged(secWith(props)).authed());
    }

    @Test
    void emptyForwardedForListFallsBack() {
        Map<String, Object> props = new HashMap<>();
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Forwarded-For", List.of());
        handshake(props, headers, null, null);
        assertEquals("unresolved", SecureHandshakeConfigurator.consumeStaged(secWith(props)).ip());
    }

    @Test
    void emptyRealIpListYieldsUnresolved() {
        Map<String, Object> props = new HashMap<>();
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Real-IP", List.of());
        handshake(props, headers, null, null);
        assertEquals("unresolved", SecureHandshakeConfigurator.consumeStaged(secWith(props)).ip());
    }
}
