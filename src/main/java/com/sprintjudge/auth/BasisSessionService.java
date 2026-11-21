package com.sprintjudge.auth;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

/**
 * Tracks browser sessions by basis-auth subject so backchannel logout can end
 * them.
 */
@Component
public class BasisSessionService implements HttpSessionListener {

	private final Map<String, HttpSession> byId = new ConcurrentHashMap<>();
	private final Map<String, Set<String>> bySubject = new ConcurrentHashMap<>();

	@Override
	public void sessionCreated(HttpSessionEvent event) {
		byId.put(event.getSession().getId(), event.getSession());
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		String id = event.getSession().getId();
		byId.remove(id);
		for (Set<String> ids : bySubject.values()) {
			ids.remove(id);
		}
	}

	public void register(String sessionId, String subject) {
		if (sessionId == null || subject == null)
			return;
		bySubject.computeIfAbsent(subject, key -> ConcurrentHashMap.newKeySet()).add(sessionId);
	}

	/**
	 * Invalidates every local session belonging to the given basis-auth subject.
	 */
	public void logout(String subject) {
		Set<String> ids = bySubject.remove(subject);
		if (ids == null)
			return;
		for (String id : ids) {
			HttpSession session = byId.get(id);
			if (session != null) {
				try {
					session.invalidate();
				} catch (IllegalStateException ignored) {
					// Already invalidated by another request.
				}
			}
		}
	}

	public int sessionCount(String subject) {
		Set<String> ids = bySubject.get(subject);
		return ids == null ? 0 : ids.size();
	}
}
