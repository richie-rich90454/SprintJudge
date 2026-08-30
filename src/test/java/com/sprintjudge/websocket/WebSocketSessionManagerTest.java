package com.sprintjudge.websocket;

import com.sprintjudge.domain.dto.ErrorMessage;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketSessionManagerTest {

    private Session openSession(String id, RemoteEndpoint.Basic remote) {
        Session s = mock(Session.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        when(s.getBasicRemote()).thenReturn(remote);
        return s;
    }

    @Test
    void registerAndUnregisterAdjustSize() {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        Session s = openSession("a", mock(RemoteEndpoint.Basic.class));
        mgr.register("a", s);
        assertEquals(1, mgr.size());
        mgr.unregister("a");
        assertEquals(0, mgr.size());
    }

    @Test
    void sendToOpenSessionWritesSerializedJson() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        mgr.register("a", openSession("a", remote));
        mgr.send("a", new ErrorMessage("X", "y"));
        var cap = forClass(String.class);
        verify(remote).sendText(cap.capture());
        assertTrue(cap.getValue().contains("\"type\":\"X\""));
    }

    @Test
    void sendRawToOpenSessionWritesJson() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        mgr.register("a", openSession("a", remote));
        mgr.sendRaw("a", "{\"k\":1}");
        verify(remote).sendText("{\"k\":1}");
    }

    @Test
    void sendToUnknownIdIsNoop() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        mgr.send("zzz", new ErrorMessage("X", "y"));
        verify(remote, never()).sendText(anyString());
    }

    @Test
    void sendToClosedSessionIsNoop() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        Session closed = mock(Session.class);
        when(closed.getId()).thenReturn("b");
        when(closed.isOpen()).thenReturn(false);
        when(closed.getBasicRemote()).thenReturn(remote);
        mgr.register("b", closed);
        mgr.send("b", new ErrorMessage("X", "y"));
        verify(remote, never()).sendText(anyString());
    }

    @Test
    void sendSwallowsIoException() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("closed")).when(remote).sendText(anyString());
        mgr.register("a", openSession("a", remote));
        mgr.send("a", new ErrorMessage("X", "y")); // must not propagate
        verify(remote).sendText(anyString());
    }

    @Test
    void broadcastEmptyDoesNothing() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        mgr.broadcast(Collections.emptyList(), new ErrorMessage("X", "y"));
        verify(remote, never()).sendText(anyString());
    }

    @Test
    void broadcastFansOutToOpenSessions() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic r1 = mock(RemoteEndpoint.Basic.class);
        RemoteEndpoint.Basic r2 = mock(RemoteEndpoint.Basic.class);
        mgr.register("a", openSession("a", r1));
        mgr.register("b", openSession("b", r2));
        mgr.broadcast(List.of("a", "b"), new ErrorMessage("X", "y"));
        var c1 = forClass(String.class);
        var c2 = forClass(String.class);
        verify(r1).sendText(c1.capture());
        verify(r2).sendText(c2.capture());
        assertTrue(c1.getValue().contains("\"type\":\"X\""));
        assertTrue(c2.getValue().contains("\"type\":\"X\""));
    }

    @Test
    void broadcastSkipsUnknownAndClosed() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic r1 = mock(RemoteEndpoint.Basic.class);
        RemoteEndpoint.Basic rClosed = mock(RemoteEndpoint.Basic.class);
        mgr.register("a", openSession("a", r1));
        Session closed = mock(Session.class);
        when(closed.getId()).thenReturn("c");
        when(closed.isOpen()).thenReturn(false);
        when(closed.getBasicRemote()).thenReturn(rClosed);
        mgr.register("c", closed);
        mgr.broadcast(List.of("a", "missing", "c"), new ErrorMessage("X", "y"));
        verify(r1).sendText(anyString());
        verify(rClosed, never()).sendText(anyString());
    }

    @Test
    void broadcastRawWritesPrerenderedJson() throws Exception {
        WebSocketSessionManager mgr = new WebSocketSessionManager();
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        mgr.register("a", openSession("a", remote));
        mgr.broadcastRaw(List.of("a"), "{\"k\":2}");
        verify(remote).sendText("{\"k\":2}");
    }
}
