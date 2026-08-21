package com.openquiz.service;

import com.openquiz.domain.models.Question;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.SubmissionRepository;
import com.openquiz.service.executor.CodeExecutor;
import com.openquiz.service.executor.JudgeRequest;
import com.openquiz.service.executor.JudgeResult;
import com.openquiz.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionProcessorTest {

    @Mock CodeExecutor executor;
    @Mock SubmissionRepository submissionRepository;
    @Mock QuestionRepository questionRepository;
    @Mock ScoringEngine scoringEngine;
    @Mock GameRoomManager roomManager;

    @Test
    void codingRunSavesBestSubmission() {
        Question q = new Question("q1", "qz", "T", "D", "OJ_FULL", null, 30, 500,
                Json.write(Map.of("testCases", List.of(Map.of("input", "1 2", "expectedOutput", "3", "isHidden", false)))), 0, null);
        when(questionRepository.findById("q1")).thenReturn(Optional.of(q));
        when(submissionRepository.findBySessionQuestion(any(), any())).thenReturn(List.of());
        when(executor.judge(any(JudgeRequest.class)))
                .thenReturn(new JudgeResult(1, 1, true, List.of(new JudgeResult.CaseResult(0, true, "3", "3", ""))));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(500);

        SubmissionProcessor p = new SubmissionProcessor(executor, submissionRepository, questionRepository,
                scoringEngine, roomManager, new Semaphore(100));
        p.processCoding("s1", "q1", "Alice", "uuid-1", "python", "print(3)", Map.of());

        verify(submissionRepository).save(any());
        verify(roomManager).broadcastLeaderboard("s1");
    }
}
