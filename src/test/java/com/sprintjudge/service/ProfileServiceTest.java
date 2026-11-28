package com.sprintjudge.service;

import com.sprintjudge.domain.profile.PlayerProfile;
import com.sprintjudge.domain.profile.ProfileStore;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("all")
class ProfileServiceTest {

	private final ProfileStore store = mock(ProfileStore.class);
	private final ProfileService service = new ProfileService(store);

	private static PlayerProfile profile() {
		return new PlayerProfile("p1", "Alice", null, "tok", 123L);
	}

	private void stubReport() {
		when(store.findById("p1")).thenReturn(Optional.of(profile()));
		when(store.history("p1")).thenReturn(List.of());
		when(store.correctness("p1")).thenReturn(List.of());
	}

	@Test
	void createWithoutLink() {
		when(store.create("Alice")).thenReturn(profile());
		stubReport();
		assertNotNull(service.create("Alice", null, null));
		verify(store, never()).link(anyString(), anyString());
	}

	@Test
	void createWithBlankUuidSkipsLink() {
		when(store.create("Alice")).thenReturn(profile());
		stubReport();
		service.create("Alice", "  ", "rt");
		verify(store, never()).validRejoin(anyString(), anyString());
	}

	@Test
	void createLinksVerifiedIdentity() {
		when(store.create("Alice")).thenReturn(profile());
		when(store.validRejoin("u1", "rt")).thenReturn(true);
		stubReport();
		service.create("Alice", " u1 ", "rt");
		verify(store).link("p1", "u1");
	}

	@Test
	void createRejectsBadNicknameAndRejoin() {
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create("!!!", null, null))
				.getStatusCode().value());
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(null, null, null))
				.getStatusCode().value());
		when(store.create("Alice")).thenReturn(profile());
		when(store.validRejoin("u1", "bad")).thenReturn(false);
		assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create("Alice", "u1", "bad"))
				.getStatusCode().value());
	}

	@Test
	void createMapsStoreConflicts() {
		when(store.create("Alice")).thenThrow(new DataIntegrityViolationException("dup"));
		assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create("Alice", null, null))
				.getStatusCode().value());
		org.mockito.Mockito.doReturn(profile()).when(store).create("Alice");
		when(store.validRejoin("u1", "rt")).thenReturn(true);
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup")).when(store).link("p1", "u1");
		assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create("Alice", "u1", "rt"))
				.getStatusCode().value());
	}

	@Test
	void getGuardsToken() {
		stubReport();
		assertNotNull(service.get("p1", "tok"));
		assertEquals(401,
				assertThrows(ResponseStatusException.class, () -> service.get("p1", "wrong")).getStatusCode().value());
		when(store.findById("missing")).thenReturn(Optional.empty());
		assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.get("missing", "tok"))
				.getStatusCode().value());
		org.mockito.Mockito.reset(store);
		when(store.findById("p1")).thenReturn(Optional.of(profile())).thenReturn(Optional.empty());
		assertEquals(404,
				assertThrows(ResponseStatusException.class, () -> service.get("p1", "tok")).getStatusCode().value());
	}

	@Test
	void updateKeepsOrChangesNickname() {
		when(store.findById("p1")).thenReturn(Optional.of(profile()));
		when(store.rename("p1", "Alice")).thenReturn(true);
		when(store.rename("p1", "Bob")).thenReturn(true);
		when(store.history("p1")).thenReturn(List.of());
		when(store.correctness("p1")).thenReturn(List.of());
		assertNotNull(service.update("p1", "tok", null, null, null));
		assertNotNull(service.update("p1", "tok", " Bob ", null, null));
		verify(store).rename("p1", "Bob");
	}

	@Test
	void updateLinksAndRejectsInvalidInputs() {
		when(store.findById("p1")).thenReturn(Optional.of(profile()));
		when(store.rename("p1", "Alice")).thenReturn(true);
		when(store.validRejoin("u1", "rt")).thenReturn(true);
		when(store.history("p1")).thenReturn(List.of());
		when(store.correctness("p1")).thenReturn(List.of());
		service.update("p1", "tok", null, "u1", "rt");
		verify(store).link("p1", "u1");
		assertEquals(400,
				assertThrows(ResponseStatusException.class, () -> service.update("p1", "tok", "!!!", null, null))
						.getStatusCode().value());
		when(store.rename("p1", "Alice")).thenReturn(false);
		assertEquals(404,
				assertThrows(ResponseStatusException.class, () -> service.update("p1", "tok", null, null, null))
						.getStatusCode().value());
	}

	@Test
	void updateGuardsProfileAndToken() {
		when(store.findById("missing")).thenReturn(Optional.empty());
		assertEquals(404,
				assertThrows(ResponseStatusException.class, () -> service.update("missing", "tok", null, null, null))
						.getStatusCode().value());
		when(store.findById("p1")).thenReturn(Optional.of(profile()));
		assertEquals(401,
				assertThrows(ResponseStatusException.class, () -> service.update("p1", "wrong", null, null, null))
						.getStatusCode().value());
	}

	@Test
	void updateMapsStoreConflicts() {
		when(store.findById("p1")).thenReturn(Optional.of(profile()));
		when(store.rename("p1", "Alice")).thenThrow(new DataIntegrityViolationException("dup")).thenReturn(true);
		assertEquals(409,
				assertThrows(ResponseStatusException.class, () -> service.update("p1", "tok", null, null, null))
						.getStatusCode().value());
		when(store.validRejoin("u1", "rt")).thenReturn(true);
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup")).when(store).link("p1", "u1");
		assertEquals(409,
				assertThrows(ResponseStatusException.class, () -> service.update("p1", "tok", null, "u1", "rt"))
						.getStatusCode().value());
	}
}
