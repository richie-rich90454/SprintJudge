package com.sprintjudge.websocket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter for WebSocket JOIN attempts, keyed by remote
 * address. A 6-digit PIN has 900k combinations; without this the join path is
 * brute-forceable. 10 failed joins per minute per IP, then hard reject for the
 * remainder of the window. Successful joins reset the counter.
 */
@Component
public class JoinRateLimiter {

	private static final int MAX_FAILURES = 10;
	private static final long WINDOW_MS = 60_000L;
	private static final int MAX_TRACKED = 10_000;

	private final Map<String, Window> windows = new ConcurrentHashMap<>();
	private final java.util.function.LongSupplier clock;

	/** Production ctor: wall clock. */
	@SuppressWarnings("all")
	public JoinRateLimiter() {
		this(new java.util.function.LongSupplier() {
			@Override
			public long getAsLong() {
				return System.currentTimeMillis();
			}
		});
	}

	/** Test ctor: injectable clock for deterministic window expiry. */
	JoinRateLimiter(java.util.function.LongSupplier clock) {
		this.clock = clock;
	}

	/** Returns false when the address is blocked for the current window. */
	public boolean tryJoin(String remoteAddr) {
		long now = clock.getAsLong();
		if (windows.size() > MAX_TRACKED) {
			java.util.Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, Window> e = it.next();
				if (now - e.getValue().windowStart > WINDOW_MS)
					it.remove();
			}
		}
		Window w = windows.computeIfAbsent(remoteAddr, new java.util.function.Function<String, Window>() {
			@Override
			public Window apply(String k) {
				return new Window(now);
			}
		});
		synchronized (w) {
			if (now - w.windowStart > WINDOW_MS) {
				w.windowStart = now;
				w.failures = 0;
			}
			return w.failures < MAX_FAILURES;
		}
	}

	public void recordFailure(String remoteAddr) {
		Window w = windows.get(remoteAddr);
		if (w == null)
			return;
		synchronized (w) {
			w.failures++;
		}
	}

	public void recordSuccess(String remoteAddr) {
		windows.remove(remoteAddr);
	}

	private static final class Window {
		long windowStart;
		int failures;
		Window(long start) {
			this.windowStart = start;
		}
	}
}
