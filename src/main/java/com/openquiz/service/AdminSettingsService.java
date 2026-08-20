package com.openquiz.service;

import com.openquiz.repository.AdminSettingsRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminSettingsService {

    private final AdminSettingsRepository repository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public AdminSettingsService(AdminSettingsRepository repository) {
        this.repository = repository;
        refresh();
    }

    public synchronized void refresh() {
        cache.clear();
        cache.putAll(repository.findAllAsMap());
    }

    public String get(String key, String defaultValue) {
        return cache.getOrDefault(key, defaultValue);
    }

    public Map<String, Object> asMap() {
        return Map.copyOf(cache);
    }

    public void set(String key, String value) {
        repository.put(key, value);
        cache.put(key, value);
    }
}
