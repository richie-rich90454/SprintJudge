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

	private final Map<String, Long> revoked = new ConcurrentHashMap<>();

	public void deny(String jti, long validAfterEpochSec) {
		if (jti == null || jti.isBlank())
			return;
		revoked.merge(jti, Long.valueOf(validAfterEpochSec), new java.util.function.BiFunction<Long, Long, Long>() {
			@Override
			public Long apply(Long first, Long second) {
				return Long.valueOf(Math.min(first.longValue(), second.longValue()));
			}
		});
	}

	public boolean isDenied(String jti) {
		if (jti == null)
			return false;
		Long after = revoked.get(jti);
		return after != null && System.currentTimeMillis() / 1000L >= after.longValue();
	}

	public int size() {
		return revoked.size();
	}
}
