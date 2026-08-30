package com.sprintjudge.service;

import com.sprintjudge.domain.dto.GameReview;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.SubmissionProcessor;
import com.sprintjudge.util.Json;
import com.sprintjudge.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameReviewTest {

    @Mock QuizRepository quizRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock QuestionRepository questionRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock ScoringEngine scoringEngine;
    @Mock SubmissionProcessor submissionProcessor;
    @Mock WebSocketSessionManager ws;
    @Mock EvaluationService evaluationService;
    @Mock AdminSettingsService settingsService;
    @Mock BroadcastScheduler scheduler;
    @Mock SubmissionWriteBuffer writeBuffer;
    @Mock RoundTimeoutScheduler roundTimer;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    private GameRoomManager manager() {
        var provider = new org.springframework.beans.factory.ObjectProvider<SubmissionProcessor>() {
            @Override public SubmissionProcessor getObject() { return submissionProcessor; }
        };
        return new GameRoomManager(sessionRepository, quizRepository, questionRepository,
                submissionRepository, scoringEngine, provider, ws,
                evaluationService, settingsService, scheduler, writeBuffer, roundTimer, eventPublisher);
    }

    private GameReview invokeBuildReview(GameRoomManager mgr, GameRoom room) throws Exception {
        Method m = GameRoomManager.class.getDeclaredMethod("buildReview", GameRoom.class);
        m.setAccessible(true);
        return (GameReview) m.invoke(mgr, room);
    }

    private GameRoom room(String pin) {
        return new GameRoom("s1", "qz", pin, "ENDED", 100, GameRoom.GameMode.STANDARD);
    }

    private Question mcq(String id, String title) {
        return new Question(id, "qz", title, "D", "MCQ", null, 30, 100,
                Json.write(Map.of("correctIndex", 0)), 0, Instant.now());
    }

    private Submission sub(String qId, String pUuid, boolean correct, int score, int attempts) {
        return new Submission("sub-" + qId + "-" + pUuid, "s1", qId, "P", pUuid,
                "{}", score, correct, null, attempts, Instant.now());
    }

    // ---------- tests ----------

    @Test
    void buildReviewReturnsCorrectStructure() throws Exception {
        GameRoomManager mgr = manager();
        GameRoom r = room("123456");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
        when(submissionRepository.findBySession("s1")).thenReturn(List.of());

        GameReview review = invokeBuildReview(mgr, r);

        assertEquals("GAME_REVIEW", review.type());
        assertNotNull(review.questions());
        assertNotNull(review.players());
        assertNotNull(review.classStats());
    }

    @Test
    void playerAnswersAreCorrectlyAggregated() throws Exception {
        GameRoomManager mgr = manager();
        GameRoom r = room("123456");
        Player p = new Player("p1", "Alice", 0, "sess", true, "tok");
        r.addPlayer(p);

        Question q1 = mcq("q1", "Q1");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1));
        when(submissionRepository.findBySession("s1")).thenReturn(List.of(
                sub("q1", "p1", true, 800, 1)));

        GameReview review = invokeBuildReview(mgr, r);

        assertEquals(1, review.players().size());
        GameReview.PlayerReview pr = review.players().get(0);
        assertEquals("p1", pr.playerUuid());
        assertEquals(800, pr.totalScore());
        assertEquals(1, pr.answers().size());
        assertTrue(pr.answers().get(0).correct());
        assertEquals(800, pr.answers().get(0).scoreEarned());
    }

    @Test
    void classStatsAreComputedCorrectly() throws Exception {
        GameRoomManager mgr = manager();
        GameRoom r = room("123456");
        Player p1 = new Player("p1", "Alice", 0, "s1", true, "t1");
        Player p2 = new Player("p2", "Bob", 0, "s2", true, "t2");
        r.addPlayer(p1);
        r.addPlayer(p2);

        Question q1 = mcq("q1", "Q1");
        Question q2 = mcq("q2", "Q2");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of(q1, q2));
        when(submissionRepository.findBySession("s1")).thenReturn(List.of(
                sub("q1", "p1", true, 800, 1),
                sub("q1", "p2", false, 0, 2),
                sub("q2", "p1", true, 900, 1),
                sub("q2", "p2", true, 700, 1)));

        GameReview review = invokeBuildReview(mgr, r);

        GameReview.ClassStats stats = review.classStats();
        assertEquals(2, stats.totalPlayers());
        assertEquals(2, stats.totalQuestions());
        assertEquals(3, stats.totalCorrect());
        assertEquals(5, stats.totalAttempts());  // 1+2+1+1
        // avgScore = (800+0+900+700) / 2 = 1200.0
        assertEquals(1200.0, stats.avgScore(), 0.1);
        // q1 correctRate = 1/2 = 0.5, q2 correctRate = 2/2 = 1.0
        // hardest = q1 (0.5), easiest = q2 (1.0)
        assertEquals("q1", stats.hardestQuestionId());
        assertEquals("q2", stats.easiestQuestionId());
    }

    @Test
    void emptyRoomProducesValidEmptyReview() throws Exception {
        GameRoomManager mgr = manager();
        GameRoom r = room("123456");
        when(questionRepository.findByQuiz("qz")).thenReturn(List.of());
        when(submissionRepository.findBySession("s1")).thenReturn(List.of());

        GameReview review = invokeBuildReview(mgr, r);

        assertTrue(review.questions().isEmpty());
        assertTrue(review.players().isEmpty());
        assertEquals(0, review.classStats().totalPlayers());
        assertEquals(0, review.classStats().avgScore());
        assertNull(review.classStats().hardestQuestionId());
        assertNull(review.classStats().easiestQuestionId());
    }
}
