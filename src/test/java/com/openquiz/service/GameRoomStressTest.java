package com.openquiz.service;

import com.openquiz.domain.models.GameSession;
import com.openquiz.repository.GameSessionRepository;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.QuizRepository;
import com.openquiz.repository.SubmissionRepository;
import com.openquiz.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Stress test: 500 concurrent WebSocket joins must all register without lost
 * updates on the room's player map.
 */
@ExtendWith(MockitoExtension.class)
class GameRoomStressTest {

    @Mock QuizRepository quizRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock QuestionRepository questionRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock ScoringEngine scoringEngine;
    @Mock SubmissionProcessor submissionProcessor;
    @SuppressWarnings("unchecked")
    private final org.springframework.beans.factory.ObjectProvider<SubmissionProcessor> processorProvider =
            (org.springframework.beans.factory.ObjectProvider<SubmissionProcessor>) org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
    @Mock WebSocketSessionManager ws;
    @Mock EvaluationService evaluationService;
    @Mock AdminSettingsService settingsService;
    @Mock BroadcastScheduler scheduler;
    @Mock SubmissionWriteBuffer writeBuffer;

    private static final int PLAYERS = 500;

    @Test
    void fiveHundredConcurrentJoinsAllRegister() throws Exception {
        GameSession session = new GameSession("s1", "qz", "123456", "host", "LOBBY", 0, null, null, null, Instant.now());
        lenient().when(sessionRepository.findByPin(anyString())).thenReturn(Optional.of(session));

        GameRoomManager mgr = new GameRoomManager(sessionRepository, quizRepository, questionRepository,
                submissionRepository, scoringEngine, processorProvider, ws, evaluationService,
                settingsService, scheduler, writeBuffer);

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(PLAYERS);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < PLAYERS; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    mgr.join("123456", "Player" + n, "sess-" + n, "player");
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Joins did not complete in time");
        pool.shutdownNow();

        assertEquals(0, failures.get());
        assertEquals(PLAYERS, mgr.getRoomState("123456").players().size());
    }
}
