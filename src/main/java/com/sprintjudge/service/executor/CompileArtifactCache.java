package com.sprintjudge.service.executor;

import com.sprintjudge.util.Sha256;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Content-addressed compiled-binary cache for identical resubmits.
 *
 * <p>Players iterate on one solution, so recompiling byte-identical sources
 * wastes the judge's CPU budget. Key = SHA-256(language | source). Only
 * single-artifact toolchains (C, C++) participate; Java emits class trees that
 * cannot be moved as one file.
 *
 * <p>Eviction: LRU by access time, bounded by entry count and total bytes.
 */
@Component
public class CompileArtifactCache {

    private record Entry(Path file, AtomicLong lastAccess) {}

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final AtomicLong totalBytes = new AtomicLong();
    private final Path dir;
    private final int maxEntries;
    private final long maxBytes;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public CompileArtifactCache(
            @Value("${sprintjudge.executor.compile-cache.dir:./executor/cache}") String dir,
            @Value("${sprintjudge.executor.compile-cache.max-entries:512}") int maxEntries,
            @Value("${sprintjudge.executor.compile-cache.max-mb:512}") long maxMb) throws IOException {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.dir);
        this.maxEntries = Math.max(16, maxEntries);
        this.maxBytes = Math.max(16L * 1024 * 1024, maxMb * 1024L * 1024L);
    }

    public Optional<Path> get(String key) {
        Entry e = map.get(key);
        if (e == null) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        if (!Files.exists(e.file())) {
            map.remove(key);
            misses.incrementAndGet();
            return Optional.empty();
        }
        e.lastAccess().set(System.nanoTime());
        hits.incrementAndGet();
        return Optional.of(e.file());
    }

    /** Moves a freshly compiled binary into the cache; best-effort, never throws. */
    public void put(String key, Path compiledBinary) {
        try {
            Path target = dir.resolve(key);
            Files.move(compiledBinary, target, StandardCopyOption.REPLACE_EXISTING);
            long size = Files.size(target);
            Entry prev = map.put(key, new Entry(target, new AtomicLong(System.nanoTime())));
            if (prev != null) {
                totalBytes.addAndGet(size - fileSize(prev.file()));
                safeDelete(prev.file());
            } else {
                totalBytes.addAndGet(size);
            }
            evictIfNeeded();
        } catch (IOException ignored) {
            // Cache is an optimization; any failure falls back to fresh compiles.
        }
    }

    private void evictIfNeeded() {
        while (map.size() > maxEntries || totalBytes.get() > maxBytes) {
            map.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().lastAccess().get()))
                    .ifPresent(eldest -> {
                        if (map.remove(eldest.getKey()) != null) {
                            totalBytes.addAndGet(-fileSize(eldest.getValue().file()));
                            safeDelete(eldest.getValue().file());
                        }
                    });
            if (map.isEmpty()) return;
        }
    }

    private long fileSize(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    private void safeDelete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {}
    }

    // ---------- metrics ----------

    public long hits() { return hits.get(); }
    public long misses() { return misses.get(); }
    public double hitRatio() {
        long h = hits.get(), m = misses.get();
        long total = h + m;
        return total == 0 ? 0.0 : (double) h / total;
    }
    public int entries() { return map.size(); }
    public long bytes() { return totalBytes.get(); }

    /** Stable cache key for a language/source pair. */
    public static String keyFor(String language, String source) {
        return Sha256.hex(language + '|' + source);
    }
}
