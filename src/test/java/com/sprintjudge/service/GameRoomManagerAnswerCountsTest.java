package com.sprintjudge.service;

import com.sprintjudge.domain.dto.RoundResult;
import com.sprintjudge.domain.enums.QuestionType;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.executor.CodeExecutor;
import com.sprintjudge.websocket.WebSocketSessionManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameRoomManagerAnswerCountsTest {

    private static Submission submission(String player, String data) {
        return new Submission("id-" + player, "s", "q", "n", player, data, 0, false, null, 1, Instant.now());
    }

    private static GameRoomManager manager(SubmissionRepository submissions) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        return new GameRoomManager(
                mock(GameSessionRepository.class),
                mock(QuizRepository.class),
                mock(QuestionRepository.class),
                submissions,
                mock(ScoringEngine.class),
                beans.getBeanProvider(SubmissionProcessor.class),
                mock(WebSocketSessionManager.class),
                mock(EvaluationService.class),
                mock(AdminSettingsService.class),
                mock(BroadcastScheduler.class),
                mock(SubmissionWriteBuffer.class),
                mock(RoundTimeoutScheduler.class),
                mock(ApplicationEventPublisher.class));
    }

    private static Question question(QuestionType type) {
        return new Question("q", "quiz", "title", "desc", type.name(), null, 60, 100, "{}", 0, Instant.now());
    }

    private static RoundResult.PlayerScore score(boolean correct) {
        return new RoundResult.PlayerScore("p", "P", 0, correct, 0, 0, 0);
    }

    @Test
    void mcqCountsLettersAndSkipsMalformedOrOutOfRangeResponses() {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        when(repo.findBySessionQuestion("s", "q")).thenReturn(List.of(
                submission("p1", "{\"selectedIndex\":1}"),
                submission("p2", "{\"selectedIndex\":1}"),
                submission("p3", "{\"selectedIndex\":30}"),
                submission("p4", "not json"),
                submission("p5", "{}"),
                submission("p6", null)));
        GameRoom room = mock(GameRoom.class);
        when(room.sessionId()).thenReturn("s");
        Map<String, Integer> counts = manager(repo).answerCounts(
                room, question(QuestionType.MCQ), QuestionType.MCQ, List.of(score(false)));
        assertEquals(2, counts.get("B"));
        assertEquals(1, counts.size());
    }

    @Test
    void outputPredictionAndComplexityUseTheMcqDistributionPath() {
        for (QuestionType type : List.of(QuestionType.OUTPUT_PRED, QuestionType.COMPLEXITY)) {
            SubmissionRepository repo = mock(SubmissionRepository.class);
            when(repo.findBySessionQuestion("s", "q"))
                    .thenReturn(List.of(submission("p1", "{\"selectedIndex\":0}")));
            GameRoom room = mock(GameRoom.class);
            when(room.sessionId()).thenReturn("s");
            Map<String, Integer> counts = manager(repo).answerCounts(
                    room, question(type), type, List.of());
            assertEquals(1, counts.get("A"));
        }
    }

    @Test
    void trueFalseCountsValuesAndSkipsMissingValues() {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        when(repo.findBySessionQuestion("s", "q")).thenReturn(List.of(
                submission("p1", "{\"value\":true}"),
                submission("p2", "{\"value\":false}"),
                submission("p3", "{}"),
                submission("p4", "not json"),
                submission("p5", null)));
        GameRoom room = mock(GameRoom.class);
        when(room.sessionId()).thenReturn("s");
        Map<String, Integer> counts = manager(repo).answerCounts(
                room, question(QuestionType.TRUE_FALSE), QuestionType.TRUE_FALSE, List.of());
        assertEquals(1, counts.get("True"));
        assertEquals(1, counts.get("False"));
    }

    @Test
    void multiSelectCountsEachSelectedLetterAndSkipsBadShapes() {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        when(repo.findBySessionQuestion("s", "q")).thenReturn(List.of(
                submission("p1", "{\"selectedIndices\":[0,2]}"),
                submission("p2", "{\"selectedIndices\":[30]}"),
                submission("p3", "{}"),
                submission("p4", "not json"),
                submission("p5", "{\"selectedIndices\":[-1]}"),
                submission("p6", null)));
        GameRoom room = mock(GameRoom.class);
        when(room.sessionId()).thenReturn("s");
        Map<String, Integer> counts = manager(repo).answerCounts(
                room, question(QuestionType.MULTIPLE_SELECT), QuestionType.MULTIPLE_SELECT, List.of());
        assertEquals(1, counts.get("A"));
        assertEquals(1, counts.get("C"));
    }

    @Test
    void unsupportedTypeFallsBackToCorrectIncorrect() {
        SubmissionRepository repo = mock(SubmissionRepository.class);
        when(repo.findBySessionQuestion("s", "q")).thenReturn(List.of());
        GameRoom room = mock(GameRoom.class);
        when(room.sessionId()).thenReturn("s");
        Map<String, Integer> counts = manager(repo).answerCounts(
                room, question(QuestionType.NUMERIC), QuestionType.NUMERIC,
                List.of(score(true), score(false), score(false)));
        assertEquals(1, counts.get("Correct"));
        assertEquals(2, counts.get("Incorrect"));
    }
}
