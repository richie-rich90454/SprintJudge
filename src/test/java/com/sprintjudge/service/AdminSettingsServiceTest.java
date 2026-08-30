package com.sprintjudge.service;

import com.sprintjudge.repository.AdminSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSettingsServiceTest {

    @Mock AdminSettingsRepository repository;
    AdminSettingsService service;

    @BeforeEach
    void setUp() {
        service = new AdminSettingsService(repository);
    }

    @Test
    void refreshLoadsCacheFromRepository() {
        when(repository.findAllAsMap()).thenReturn(Map.of("k1", "v1", "k2", "v2"));
        service.refresh();
        assertEquals("v1", service.get("k1", "def"));
        assertEquals("v2", service.get("k2", "def"));
        assertTrue(service.asMap().containsKey("k1"));
    }

    @Test
    void getUsesDefaultWhenKeyAbsentAndTriggersLazyLoad() {
        when(repository.findAllAsMap()).thenReturn(Map.of());
        // first access triggers lazy load via ensureLoaded()
        assertEquals("def", service.get("missing", "def"));
        verify(repository).findAllAsMap();
    }

    @Test
    void getSkipsReloadWhenAlreadyLoaded() {
        when(repository.findAllAsMap()).thenReturn(Map.of("a", "1"));
        service.refresh();            // loaded = true
        service.get("a", "x");         // ensureLoaded: !loaded is false -> no refresh
        verify(repository, times(1)).findAllAsMap();
    }

    @Test
    void asMapReturnsImmutableCopy() {
        when(repository.findAllAsMap()).thenReturn(Map.of("a", "1"));
        service.refresh();
        Map<String, Object> m = service.asMap();
        assertEquals("1", m.get("a"));
        assertThrows(UnsupportedOperationException.class, () -> m.put("b", "2"));
    }

    @Test
    void setPersistsAndUpdatesCacheWhenAlreadyLoaded() {
        when(repository.findAllAsMap()).thenReturn(Map.of());
        service.refresh();            // loaded = true so ensureLoaded is a no-op
        service.set("newKey", "newVal");
        verify(repository).put("newKey", "newVal");
        assertEquals("newVal", service.get("newKey", "def"));
    }

    @Test
    void setTriggersLazyLoadWhenNotLoaded() {
        when(repository.findAllAsMap()).thenReturn(Map.of());
        service.set("k", "v");         // ensureLoaded -> refresh (findAllAsMap)
        verify(repository).put("k", "v");
        verify(repository).findAllAsMap();
        assertEquals("v", service.get("k", "def"));
    }
}
