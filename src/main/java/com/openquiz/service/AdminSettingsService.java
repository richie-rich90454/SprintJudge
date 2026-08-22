package com.openquiz.service;

import com.openquiz.repository.AdminSettingsRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached admin settings. The cache loads LAZILY on first access so that bean
 * construction never races Flyway migrations on a fresh database (a real
 * production-boot failure mode caught by the Windows launch verification).
 */
@Service
public class AdminSettingsService {

    private final AdminSettingsRepository repository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public AdminSettingsService(AdminSettingsRepository repository) {
        this.repository = repository;
    }

    public synchronized void refresh() {
        cache.clear();
        cache.putAll(repository.findAllAsMap());
        loaded = true;
    }

    public String get(String key, String defaultValue) {
        ensureLoaded();
        return cache.getOrDefault(key, defaultValue);
    }

    public Map<String, Object> asMap() {
        ensureLoaded();
        return Map.copyOf(cache);
    }

    public void set(String key, String value) {
        repository.put(key, value);
        ensureLoaded();
        cache.put(key, value);
    }

    private void ensureLoaded() {
        if (!loaded) refresh();
    }
}
