package com.sprintjudge.service;

import com.sprintjudge.repository.AdminSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSettingsServiceTest {

    @Mock AdminSettingsRepository repository;

    private AdminSettingsService service(Map<String, String> seed) {
        when(repository.findAllAsMap()).thenReturn(seed);
        return new AdminSettingsService(repository);
    }

    @Test
    void cacheServesSeededValues() {
        AdminSettingsService s = service(Map.of("mcq_max_attempts", "3"));
        assertEquals("3", s.get("mcq_max_attempts", "1"));
    }

    @Test
    void missingKeyFallsBackToDefault() {
        assertEquals("60", service(Map.of()).get("default_time_limit", "60"));
    }

    @Test
    void setWritesThroughAndUpdatesCache() {
        AdminSettingsService s = service(Map.of());
        s.set("new_key", "42");
        verify(repository).put("new_key", "42");
        assertEquals("42", s.get("new_key", "0"));
    }

    @Test
    void setOverwritesExistingKeyInCache() {
        AdminSettingsService s = service(Map.of("k", "v1"));
        s.set("k", "v2");
        verify(repository).put("k", "v2");
        assertEquals("v2", s.get("k", "v1"));
    }

    @Test
    void refreshPicksUpExternalChanges() {
        AdminSettingsService s = service(Map.of("k", "old"));
        when(repository.findAllAsMap()).thenReturn(Map.of("k", "new"));
        s.refresh();
        assertEquals("new", s.get("k", "old"));
    }

    @Test
    void asMapSnapshotIsComplete() {
        Map<String, Object> snapshot = service(Map.of("a", "1", "b", "2")).asMap();
        assertEquals(2, snapshot.size());
        assertEquals("1", snapshot.get("a"));
    }
}
