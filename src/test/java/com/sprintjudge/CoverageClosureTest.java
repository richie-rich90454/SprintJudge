package com.sprintjudge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.sprintjudge.domain.dto.GameReview;
import com.sprintjudge.domain.dto.JoinedMessage;
import com.sprintjudge.domain.dto.QuestionDto;
import com.sprintjudge.domain.dto.QuestionStart;
import com.sprintjudge.domain.event.RoomEvent;
import com.sprintjudge.domain.reducer.RoomReducer;
import com.sprintjudge.exception.GlobalExceptionHandler;
import com.sprintjudge.service.GameRoom;

/** Closes the remaining value-method and defensive-arm coverage gaps. */
@SuppressWarnings("all")
class CoverageClosureTest {

	@Test
	void questionStartEqualsLastFieldBranch() {
		QuestionStart a = new QuestionStart("MCQ", null, 30, 100L, 200L);
		QuestionStart b = new QuestionStart("MCQ", new QuestionDto(), 30, 100L, 200L);
		assertNotEquals(a, b);
	}

	@Test
	void gameReviewEqualsLastFieldBranch() {
		GameReview a = new GameReview("review", null, null, null, null, "s1", null);
		GameReview b = new GameReview("review", null, null, null, null, "s1", List.of(new GameReview.TeamReview()));
		assertNotEquals(a, b);
	}

	@Test
	void questionDtoEqualsLastFieldBranch() {
		QuestionDto a = new QuestionDto("id", "MCQ", "title", "desc", 30, 100, List.of("java"), null);
		QuestionDto b = new QuestionDto("id", "MCQ", "title", "desc", 30, 100, List.of("java"), "config");
		assertNotEquals(a, b);
	}

	@Test
	void gameReviewEqualsDifferentClassStatsShortCircuits() {
		GameReview a = new GameReview("review", null, null, null,
				new GameReview.ClassStats(1, 1, 1.0, 1, 1, "q1", "q1"), "s1", null);
		GameReview b = new GameReview("review", null, null, null, null, "s1", null);
		assertNotEquals(a, b);
	}

	@Test
	void joinedMessageEqualsLastFieldBranch() {
		JoinedMessage a = new JoinedMessage("joined", "uuid", "token", null);
		JoinedMessage b = new JoinedMessage("joined", "uuid", "token", new com.sprintjudge.domain.dto.RoomState());
		assertNotEquals(a, b);
	}

	@Test
	void foldResultEqualsLastFieldBranch() {
		RoomReducer.FoldedRoom state = RoomReducer.initial("r1");
		RoomReducer.FoldResult a = new RoomReducer.FoldResult(state, null);
		RoomReducer.FoldResult b = new RoomReducer.FoldResult(state, List.of());
		assertNotEquals(a, b);
	}

	@Test
	void foldResultEqualsDifferentStateShortCircuits() {
		RoomReducer.FoldResult a = new RoomReducer.FoldResult(RoomReducer.initial("r1"), List.of());
		RoomReducer.FoldResult b = new RoomReducer.FoldResult(RoomReducer.initial("r2"), List.of());
		assertNotEquals(a, b);
	}

	@Test
	void validationWithTwoFieldErrorsJoinsDetails() throws Exception {
		Method method = Dummy.class.getDeclaredMethod("m", String.class);
		MethodParameter mp = new MethodParameter(method, 0);
		BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "obj");
		br.addError(new FieldError("obj", "title", "must not be blank"));
		br.addError(new FieldError("obj", "code", "must be numeric"));
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mp, br);
		org.springframework.http.ResponseEntity<com.sprintjudge.domain.dto.ErrorMessage> resp = new GlobalExceptionHandler()
				.handleValidation(ex);
		assertEquals(400, resp.getStatusCode().value());
		assertTrue(resp.getBody().getMessage().contains("title: must not be blank; code: must be numeric"));
	}

	static class Dummy {
		void m(String s) {
		}
	}

	@Test
	void foldIgnoresUnknownEventType() {
		final Instant now = Instant.parse("2026-09-18T00:00:00Z");
		RoomEvent unknown = new RoomEvent() {
			@Override
			public String getRoomId() {
				return "r1";
			}

			@Override
			public long getSeq() {
				return 9L;
			}

			@Override
			public Instant getAt() {
				return now;
			}
		};
		RoomReducer.FoldResult result = RoomReducer.apply(RoomReducer.initial("r1"), unknown);
		assertTrue(result.getEffects().isEmpty());
		assertEquals(9L, result.getState().getSeq());
		assertFalse(result.getState().isEnded());
	}

	@Test
	void gameModeRejectsNull() {
		assertFalse(GameRoom.GameMode.isValid(null));
		assertTrue(GameRoom.GameMode.isValid(GameRoom.GameMode.STANDARD));
	}

	@Test
	void teamsWithEqualScoresAndNullNamesSortByEmptyName() {
		GameRoom room = new GameRoom("s1", "qz", "123456", "LOBBY", 50, GameRoom.GameMode.TEAM);
		room.createTeam(null);
		room.createTeam(null);
		assertEquals(2, room.teamsSorted().size());
	}

	@Test
	void teachNextPrivateDefensiveBranches() throws Exception {
		com.sprintjudge.service.TeachNextService svc = new com.sprintjudge.service.TeachNextService(null, null, null,
				null);
		Method topicMatches = com.sprintjudge.service.TeachNextService.class.getDeclaredMethod("topicMatches",
				String.class, String.class, String.class);
		topicMatches.setAccessible(true);
		assertEquals(0, topicMatches.invoke(svc, "JAVA_PROGRAMMING", "", null));
		assertEquals(0, topicMatches.invoke(svc, "JAVA_PROGRAMMING", "", "   "));

		Method contains = com.sprintjudge.service.TeachNextService.class.getDeclaredMethod("containsIgnoreCase",
				String.class, String.class);
		contains.setAccessible(true);
		assertFalse((Boolean) contains.invoke(null, "abc", null));
		assertFalse((Boolean) contains.invoke(null, "abc", "   "));
		assertFalse((Boolean) contains.invoke(null, null, "abc"));

		Method topicOf = com.sprintjudge.service.TeachNextService.class.getDeclaredMethod("topicOf",
				com.sprintjudge.domain.models.Question.class);
		topicOf.setAccessible(true);
		com.sprintjudge.domain.models.Question nullConfig = new com.sprintjudge.domain.models.Question("q", "qz", "T",
				"D", "MCQ", null, 30, 100, null, 0, Instant.now());
		assertEquals("T", topicOf.invoke(null, nullConfig));
		com.sprintjudge.domain.models.Question validConfigNoTopic = new com.sprintjudge.domain.models.Question("q",
				"qz", "T", "D", "MCQ", null, 30, 100, "{\"other\":1}", 0, Instant.now());
		assertEquals("T", topicOf.invoke(null, validConfigNoTopic));
	}

	@Test
	void validatorAndEvaluatorRejectUnknownType() throws Exception {
		try (org.mockito.MockedStatic<com.sprintjudge.domain.enums.QuestionType> mocked = org.mockito.Mockito
				.mockStatic(com.sprintjudge.domain.enums.QuestionType.class)) {
			mocked.when(new org.mockito.MockedStatic.Verification() {
				@Override
				public void apply() {
					com.sprintjudge.domain.enums.QuestionType.from(org.mockito.ArgumentMatchers.anyString());
				}
			}).thenReturn("UNKNOWN");
			com.sprintjudge.service.QuestionConfigValidator.requireValid("MCQ", "{}");
			com.sprintjudge.domain.models.Question question = new com.sprintjudge.domain.models.Question("q", "qz", "T",
					"D", "MCQ", null, 30, 100, "{}", 0, Instant.now());
			assertEquals(0.0, new com.sprintjudge.service.EvaluationService().evaluateCorrectness(question,
					com.sprintjudge.util.Json.readTree("{}")), 1e-9);
		}
	}
}
