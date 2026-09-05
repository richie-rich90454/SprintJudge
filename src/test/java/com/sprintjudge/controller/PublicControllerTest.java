package com.sprintjudge.controller;

import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    @Mock QuizRepository quizRepository;
    @Mock GameSessionRepository sessionRepository;

    @InjectMocks PublicController controller;

    private GameSession session(String pin, String status) {
        return new GameSession("s", "q1", pin, "host", status, "STANDARD", 0, null, null, null, Instant.now());
    }

    @Test
    void noPinListsNothing() {
        assertTrue(controller.listQuizzes(null).isEmpty());
        assertTrue(controller.listQuizzes("  ").isEmpty());
    }

    @Test
    void unknownPinListsNothing() {
        when(sessionRepository.findByPin("000000")).thenReturn(Optional.empty());
        assertTrue(controller.listQuizzes("000000").isEmpty());
    }

    @Test
    void endedPinListsNothing() {
        when(sessionRepository.findByPin("111111"))
                .thenReturn(Optional.of(session("111111", "ENDED")));
        assertTrue(controller.listQuizzes("111111").isEmpty());
    }

    @Test
    void livePinReturnsItsQuiz() {
        when(sessionRepository.findByPin("123456"))
                .thenReturn(Optional.of(session("123456", "LOBBY")));
        when(quizRepository.findById("q1"))
                .thenReturn(Optional.of(new Quiz("q1", "T", null, null, null, false)));
        assertEquals(1, controller.listQuizzes("123456").size());
    }

    @Test
    void livePinWithMissingQuizListsNothing() {
        when(sessionRepository.findByPin("222222"))
                .thenReturn(Optional.of(session("222222", "ACTIVE")));
        when(quizRepository.findById("q1")).thenReturn(Optional.empty());
        assertTrue(controller.listQuizzes(" 222222 ").isEmpty());
    }
}
