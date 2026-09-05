package com.sprintjudge.websocket;

import com.sprintjudge.domain.dto.ErrorMessage;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketSessionManagerBreadthTest {

    private Session openSession(RemoteEndpoint.Basic remote) {
        Session s = mock(Session.class);
        when(s.isOpen()).thenReturn(true);
        when(s.getBasicRemote()).thenReturn(remote);
        return s;
    }

    @Test
    void registerOverwritesPreviousSession() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic first = mock(RemoteEndpoint.Basic.class);
        RemoteEndpoint.Basic second = mock(RemoteEndpoint.Basic.class);
        mgr.register("a", openSession(first));
        mgr.register("a", openSession(second));
        assertEquals(1, mgr.size());
        mgr.send("a", new ErrorMessage("X", "y"));
        verify(first, never()).sendText(anyString());
        verify(second).sendText(anyString());
    }

    @Test
    void unregisterUnknownKeepsSize() {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        mgr.register("a", openSession(mock(RemoteEndpoint.Basic.class)));
        mgr.unregister("ghost");
        assertEquals(1, mgr.size());
    }

    @Test
    void sendNullMessageWritesNullLiteral() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        mgr.register("a", openSession(remote));
        mgr.send("a", null);
        verify(remote).sendText("null");
    }

    @Test
    void broadcastRawToMissingIdsIsSilent() {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        mgr.broadcastRaw(List.of("ghost-1", "ghost-2"), "{\"k\":1}");
        assertEquals(0, mgr.size());
    }

    @Test
    void broadcastIsolatesFailingSession() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic bad = mock(RemoteEndpoint.Basic.class);
        RemoteEndpoint.Basic good = mock(RemoteEndpoint.Basic.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("down")).when(bad).sendText(anyString());
        mgr.register("bad", openSession(bad));
        mgr.register("good", openSession(good));
        mgr.broadcast(List.of("bad", "good"), new ErrorMessage("X", "y"));
        verify(good).sendText(anyString());
    }

    @Test
    void broadcastRawEmptyListIsNoop() {
        new WebSocketSessionManager().broadcastRaw(List.of(), "{\"k\":1}");
    }

    @Test
    void sendSwallowsIllegalState() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("closed")).when(remote).sendText(anyString());
        mgr.register("a", openSession(remote));
        mgr.send("a", new ErrorMessage("X", "y"));
        verify(remote).sendText(anyString());
    }

    @Test
    void closeUnknownKeepsRegisteredSessions() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        Session s = openSession(mock(RemoteEndpoint.Basic.class));
        mgr.register("a", s);
        mgr.register("b", openSession(mock(RemoteEndpoint.Basic.class)));
        mgr.close("ghost");
        assertEquals(2, mgr.size());
        verify(s, never()).close();
    }

    @Test
    void closeNullKeepsSessions() {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        mgr.register("a", openSession(mock(RemoteEndpoint.Basic.class)));
        mgr.close(null);
        assertEquals(1, mgr.size());
    }

    @Test
    void sizeTracksMultipleSessions() {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        assertEquals(0, mgr.size());
        mgr.register("a", openSession(mock(RemoteEndpoint.Basic.class)));
        mgr.register("b", openSession(mock(RemoteEndpoint.Basic.class)));
        mgr.register("c", openSession(mock(RemoteEndpoint.Basic.class)));
        assertEquals(3, mgr.size());
        mgr.unregister("b");
        assertEquals(2, mgr.size());
    }

    @Test
    void sendAfterUnregisterIsNoop() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        mgr.register("a", openSession(remote));
        mgr.unregister("a");
        mgr.send("a", new ErrorMessage("X", "y"));
        verify(remote, never()).sendText(anyString());
    }

    @Test
    void broadcastDeliversIdenticalPayloadToAll() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic r1 = mock(RemoteEndpoint.Basic.class);
        RemoteEndpoint.Basic r2 = mock(RemoteEndpoint.Basic.class);
        mgr.register("a", openSession(r1));
        mgr.register("b", openSession(r2));
        mgr.broadcast(List.of("a", "b"), new ErrorMessage("X", "same"));
        var c1 = forClass(String.class);
        var c2 = forClass(String.class);
        verify(r1).sendText(c1.capture());
        verify(r2).sendText(c2.capture());
        assertEquals(c1.getValue(), c2.getValue());
    }
}
