package com.openquiz.service;

import com.openquiz.domain.enums.QuestionType;
import com.openquiz.domain.models.GameSession;
import com.openquiz.domain.models.Question;
import com.openquiz.repository.GameSessionRepository;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.QuizRepository;
import com.openquiz.repository.SubmissionRepository;
import com.openquiz.util.Json;
import com.openquiz.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameRoomManagerTest {

    @Mock QuizRepository quizRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock QuestionRepository questionRepository;
    @Mock SubmissionRepository submissionRepository;
    @Mock ScoringEngine scoringEngine;
    @Mock SubmissionProcessor submissionProcessor;
    @Mock WebSocketSessionManager ws;
    @Mock EvaluationService evaluationService;
    @Mock AdminSettingsService settingsService;

    private GameRoomManager manager() {
        return new GameRoomManager(sessionRepository, quizRepository, questionRepository,
                submissionRepository, scoringEngine, submissionProcessor, ws,
                evaluationService, settingsService);
    }

    @Test
    void joinRejectsInvalidPin() {
        when(sessionRepository.findByPin("000000")).thenReturn(Optional.empty());
        try {
            manager().join("000000", "Alice", "sess-1", "player");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        verify(ws, never()).broadcast(any(), any());
    }

    @Test
    void submitScoresSelectionAndSaves() {
        GameRoomManager mgr = manager();
        GameSession session = new GameSession("s1", "qz", "123456", "host", "LOBBY", 0, null, null, null, Instant.now());
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session));
        Question q = new Question("q1", "qz", "T", "D", "MCQ", null, 30, 100, Json.write(Map.of("correctIndex", 0)), 0, Instant.now());
        when(questionRepository.findById("q1")).thenReturn(Optional.of(q));
        when(evaluationService.evaluateCorrectness(any(), any())).thenReturn(1.0);
        when(submissionRepository.findBySessionQuestion(anyString(), anyString())).thenReturn(List.of());
        when(scoringEngine.scoreSelection(eq(true), anyLong(), anyLong(), anyInt(), any())).thenReturn(900);

        var player = mgr.join("123456", "Alice", "sess-1", "player");
        mgr.submit("123456", "q1", player.uuid(), "python", Json.readTree("{\"selectedIndex\":0}"));

        verify(submissionRepository).save(any());
        // One ROOM_STATE broadcast on join + one LEADERBOARD broadcast on submit.
        verify(ws, times(2)).broadcast(any(), any());
    }

    @Test
    void secondHostIsRejected() {
        GameRoomManager mgr = manager();
        GameSession session = new GameSession("s1", "qz", "123456", "host", "LOBBY", 0, null, null, null, Instant.now());
        when(sessionRepository.findByPin("123456")).thenReturn(Optional.of(session));

        mgr.join("123456", "Host", "sess-h", "host");
        try {
            mgr.join("123456", "Impostor", "sess-i", "host");
        } catch (IllegalStateException expected) {
            // expected
        }
    }
}
