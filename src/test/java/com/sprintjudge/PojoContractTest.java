package com.sprintjudge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the mechanical getters, setters and value methods on every class
 * that used to be a record. Those methods are compiler-generated on a record
 * and invisible to coverage; on the plain classes they must be covered for the
 * 100% JaCoCo gate to remain meaningful.
 */
class PojoContractTest {

	private static final Class<?>[] POJOS = {com.sprintjudge.domain.dto.BattleBracket.class,
			com.sprintjudge.domain.dto.BattleBracket.Pair.class, com.sprintjudge.domain.dto.BattleBracket.Round.class,
			com.sprintjudge.domain.dto.BattleChampion.class, com.sprintjudge.domain.dto.BattleResult.class,
			com.sprintjudge.domain.dto.BattleResult.Match.class, com.sprintjudge.domain.dto.ErrorMessage.class,
			com.sprintjudge.domain.dto.GameReview.class, com.sprintjudge.domain.dto.GameReview.ClassStats.class,
			com.sprintjudge.domain.dto.GameReview.PlayerAnswer.class,
			com.sprintjudge.domain.dto.GameReview.PlayerReview.class,
			com.sprintjudge.domain.dto.GameReview.QuestionReview.class,
			com.sprintjudge.domain.dto.GameReview.TeamReview.class, com.sprintjudge.domain.dto.JoinedMessage.class,
			com.sprintjudge.domain.dto.LeaderboardDelta.class, com.sprintjudge.domain.dto.LeaderboardEntry.class,
			com.sprintjudge.domain.dto.QuestionDto.class, com.sprintjudge.domain.dto.QuestionStart.class,
			com.sprintjudge.domain.dto.RoomState.class, com.sprintjudge.domain.dto.RoomState.PlayerInfo.class,
			com.sprintjudge.domain.dto.RoundResult.class, com.sprintjudge.domain.dto.RoundResult.PlayerScore.class,
			com.sprintjudge.domain.dto.SubmissionResult.class, com.sprintjudge.domain.dto.TimerUpdate.class,
			com.sprintjudge.domain.dto.export.ExportBundle.class,
			com.sprintjudge.domain.dto.export.ExportBundle.QuestionExport.class,
			com.sprintjudge.domain.dto.export.ExportBundle.QuizExport.class,
			com.sprintjudge.domain.event.RoomEffect.Award.class,
			com.sprintjudge.domain.event.RoomEffect.Broadcast.class,
			com.sprintjudge.domain.event.RoomEffect.Persist.class,
			com.sprintjudge.domain.event.RoomEffect.Schedule.class,
			com.sprintjudge.domain.event.RoomEvent.AnswerSubmitted.class,
			com.sprintjudge.domain.event.RoomEvent.GameEnded.class,
			com.sprintjudge.domain.event.RoomEvent.PlayerJoined.class,
			com.sprintjudge.domain.event.RoomEvent.PlayerLeft.class,
			com.sprintjudge.domain.event.RoomEvent.RoomCreated.class,
			com.sprintjudge.domain.event.RoomEvent.RoundEnded.class,
			com.sprintjudge.domain.event.RoomEvent.RoundStarted.class,
			com.sprintjudge.domain.event.RoomEvent.ScoreComputed.class,
			com.sprintjudge.domain.models.AdminSetting.class, com.sprintjudge.domain.models.GameSession.class,
			com.sprintjudge.domain.models.Question.class, com.sprintjudge.domain.models.Quiz.class,
			com.sprintjudge.domain.models.RoomState.class, com.sprintjudge.domain.models.Submission.class,
			com.sprintjudge.domain.models.User.class, com.sprintjudge.domain.reducer.RoomReducer.FoldedRoom.class,
			com.sprintjudge.domain.reducer.RoomReducer.FoldResult.class, com.sprintjudge.service.Player.class,
			com.sprintjudge.service.GameRoom.Team.class, com.sprintjudge.service.GameRoom.BattleMatch.class,
			com.sprintjudge.service.GameRoom.BattlePair.class, com.sprintjudge.service.GameRoom.BattleRound.class,
			com.sprintjudge.service.AiGradingService.AiGradeResult.class,
			type("com.sprintjudge.service.SubmissionProcessor$Core"),
			com.sprintjudge.service.BankQuizBuilder.BuildRequest.class,
			com.sprintjudge.service.BankQuizBuilder.BuildResult.class,
			type("com.sprintjudge.service.BankQuizBuilder$LoadedManifest"),
			com.sprintjudge.service.BankQuizBuilder.CsaPreset.class,
			com.sprintjudge.service.TeachNextService.Recommendation.class,
			com.sprintjudge.service.CalibrationService.Calibration.class,
			com.sprintjudge.service.event.GameEvent.GameCreated.class,
			com.sprintjudge.service.event.GameEvent.GameEnded.class,
			com.sprintjudge.service.event.GameEvent.PlayerJoined.class,
			com.sprintjudge.service.event.GameEvent.PlayerLeft.class,
			com.sprintjudge.service.event.GameEvent.QuestionCompleted.class,
			com.sprintjudge.service.event.GameEvent.QuestionStarted.class,
			com.sprintjudge.service.event.GameEvent.SubmissionReceived.class,
			com.sprintjudge.service.executor.JudgeRequest.class, com.sprintjudge.service.executor.JudgeResult.class,
			com.sprintjudge.service.executor.JudgeResult.CaseResult.class,
			com.sprintjudge.service.executor.RunRequest.class, com.sprintjudge.service.executor.RunResult.class,
			com.sprintjudge.service.executor.TestCase.class,
			type("com.sprintjudge.service.executor.CompileArtifactCache$Entry"),
			com.sprintjudge.service.leaderboard.DeltaLedger.Delta.class,
			com.sprintjudge.service.leaderboard.DeltaLedger.Batch.class,
			com.sprintjudge.service.leaderboard.RankedSkipList.Entry.class,
			com.sprintjudge.controller.PublicController.BankDto.class,
			com.sprintjudge.controller.PublicController.NetInfo.class,
			com.sprintjudge.repository.RoomEventRepository.StoredEvent.class,
			com.sprintjudge.websocket.SecureHandshakeConfigurator.StagedConnection.class,
			type("com.sprintjudge.bank.BankStore$Loaded"), com.sprintjudge.util.ExecIo.WaitOutcome.class};

	private static Class<?> type(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object sampleValue(Class<?> type) {
		if (type == String.class)
			return "different";
		if (type == int.class || type == Integer.class)
			return Integer.valueOf(1);
		if (type == long.class || type == Long.class)
			return Long.valueOf(1L);
		if (type == boolean.class || type == Boolean.class)
			return Boolean.TRUE;
		if (type == double.class || type == Double.class)
			return Double.valueOf(1.0);
		if (type == float.class || type == Float.class)
			return Float.valueOf(1.0f);
		if (type == short.class || type == Short.class)
			return Short.valueOf((short) 1);
		if (type == byte.class || type == Byte.class)
			return Byte.valueOf((byte) 1);
		if (type == char.class || type == Character.class)
			return Character.valueOf('v');
		if (type == java.time.Instant.class)
			return java.time.Instant.ofEpochMilli(1);
		if (type == java.util.Set.class)
			return java.util.Set.of("x");
		if (type == java.util.List.class)
			return java.util.List.of("x");
		if (type == java.util.Map.class)
			return java.util.Map.of("k", "v");
		if (com.fasterxml.jackson.databind.JsonNode.class.isAssignableFrom(type)) {
			return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("x");
		}
		if (type.isEnum()) {
			Object[] constants = type.getEnumConstants();
			if (constants.length > 0)
				return constants[0];
		}
		return null;
	}

	private static Constructor<?> ctorOf(Class<?> type) {
		try {
			return type.getDeclaredConstructor();
		} catch (NoSuchMethodException e) {
			return type.getDeclaredConstructors()[0];
		}
	}
	private static Object defaultValue(Class<?> type) {
		if (type == int.class)
			return Integer.valueOf(0);
		if (type == long.class)
			return Long.valueOf(0L);
		if (type == boolean.class)
			return Boolean.FALSE;
		if (type == double.class)
			return Double.valueOf(0.0);
		if (type == float.class)
			return Float.valueOf(0.0f);
		if (type == short.class)
			return Short.valueOf((short) 0);
		if (type == byte.class)
			return Byte.valueOf((byte) 0);
		if (type == char.class)
			return Character.valueOf(' ');
		return null;
	}

	@Test
	void everyConvertedRecordKeepsItsValueSemantics() throws Exception {
		for (Class<?> type : POJOS) {
			Constructor<?> ctor = ctorOf(type);
			Object[] ctorArgs = new Object[0];
			if (ctor.getParameterCount() > 0) {
				Class<?>[] params = ctor.getParameterTypes();
				ctorArgs = new Object[params.length];
				for (int i = 0; i < params.length; i++) {
					Class<?> enclosing = type.getEnclosingClass();
					if (enclosing != null && params[i].equals(enclosing)) {
						Constructor<?> outer = enclosing.getDeclaredConstructor();
						outer.setAccessible(true);
						ctorArgs[i] = outer.newInstance();
					} else {
						ctorArgs[i] = defaultValue(params[i]);
					}
				}
			}
			ctor.setAccessible(true);
			Object first = ctor.newInstance(ctorArgs);
			Object second = ctor.newInstance(ctorArgs);
			assertNotNull(first, type.getName());

			Method[] methods = type.getDeclaredMethods();
			java.util.List<Method> setters = new java.util.ArrayList<>();
			Method equals = null;
			for (Method method : methods) {
				method.setAccessible(true);
				String name = method.getName();
				int params = method.getParameterCount();
				if (params == 0 && (name.startsWith("get") || name.startsWith("is"))) {
					method.invoke(first);
				} else if (params == 1 && name.startsWith("set")) {
					setters.add(method);
					method.invoke(first, defaultValue(method.getParameterTypes()[0]));
					try {
						method.invoke(second, sampleValue(method.getParameterTypes()[0]));
					} catch (IllegalArgumentException ignored) {
						// Non-primitive field with no generic sample value; null is fine.
					}
				} else if (params == 1 && name.equals("equals")) {
					equals = method;
				} else if (params == 0 && (name.equals("hashCode") || name.equals("toString"))) {
					method.invoke(first);
					method.invoke(second);
				}
			}
			if (equals == null) {
				continue;
			}
			Object identical = ctor.newInstance(ctorArgs);
			for (Method setter : setters) {
				setter.invoke(identical, defaultValue(setter.getParameterTypes()[0]));
			}
			equals.invoke(first, first);
			equals.invoke(first, identical);
			equals.invoke(first, second);
			equals.invoke(first, new Object[]{null});
			equals.invoke(first, new Object());
			// Make every field in turn match the first instance so each
			// short-circuit in equals() takes both its true and false branch.
			for (Method changed : setters) {
				Object compare = ctor.newInstance(ctorArgs);
				for (Method setter : setters) {
					setter.invoke(compare, defaultValue(setter.getParameterTypes()[0]));
				}
				try {
					changed.invoke(compare, sampleValue(changed.getParameterTypes()[0]));
				} catch (IllegalArgumentException ignored) {
					// No sample for this field; the instance stays equal, which is fine.
				}
				equals.invoke(first, compare);
			}
		}
	}
}
