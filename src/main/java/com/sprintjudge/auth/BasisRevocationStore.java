package com.sprintjudge.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Denylist of revoked basis-auth access-token JTIs, keyed by earliest
 * valid-after.
 */
@Component
public class BasisRevocationStore {

	private static final int MAX_ENTRIES = 5000;
	private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;

	private final Map<String, Long> revoked = new ConcurrentHashMap<>();
	private final Map<String, Long> storedAt = new ConcurrentHashMap<>();

	public void deny(String jti, long validAfterEpochSec) {
		if (jti == null || jti.isBlank())
			return;
		revoked.merge(jti, Long.valueOf(validAfterEpochSec), new java.util.function.BiFunction<Long, Long, Long>() {
			@Override
			public Long apply(Long first, Long second) {
				return Long.valueOf(Math.min(first.longValue(), second.longValue()));
			}
		});
		storedAt.put(jti, System.currentTimeMillis());
		prune();
	}

	public boolean isDenied(String jti) {
		if (jti == null)
			return false;
		Long after = revoked.get(jti);
		return after != null && System.currentTimeMillis() / 1000L >= after.longValue();
	}

	/** Bounds memory: a signed token feed must not grow the denylist forever. */
	private void prune() {
		if (revoked.size() <= MAX_ENTRIES)
			return;
		long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
		for (Map.Entry<String, Long> entry : storedAt.entrySet()) {
			if (entry.getValue() < cutoff) {
				revoked.remove(entry.getKey());
				storedAt.remove(entry.getKey());
			}
		}
	}

	public int size() {
		return revoked.size();
	}
}
