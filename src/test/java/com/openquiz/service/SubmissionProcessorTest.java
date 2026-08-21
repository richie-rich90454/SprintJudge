package com.openquiz.service;

import com.openquiz.domain.models.Question;
import com.openquiz.domain.models.Submission;
import com.openquiz.repository.QuestionRepository;
import com.openquiz.repository.SubmissionRepository;
import com.openquiz.service.executor.CodeExecutor;
import com.openquiz.service.executor.JudgeRequest;
import com.openquiz.service.executor.JudgeResult;
import com.openquiz.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionProcessorTest {

    @Mock CodeExecutor executor;
    @Mock SubmissionRepository submissionRepository;
    @Mock QuestionRepository questionRepository;
    @Mock ScoringEngine scoringEngine;
    @Mock GameRoomManager roomManager;

    private SubmissionProcessor processor() {
        return new SubmissionProcessor(executor, submissionRepository, questionRepository,
                scoringEngine, roomManager, new Semaphore(100));
    }

    private Question ojQuestion() {
        return new Question("q1", "qz", "T", "D", "OJ_FULL", null, 30, 500,
                Json.write(Map.of("testCases",
                        List.of(Map.of("input", "1 2", "expectedOutput", "3", "isHidden", false)))),
                0, null);
    }

    private JudgeResult result(int passed, int total) {
        List<JudgeResult.CaseResult> cases = new java.util.ArrayList<>();
        for (int i = 0; i < total; i++) {
            cases.add(new JudgeResult.CaseResult(i, i < passed, "x", i < passed ? "x" : "y", ""));
        }
        return new JudgeResult(passed, total, passed == total, cases);
    }

    // ---------- happy path ----------

    @Test
    void codingRunSavesBestSubmission() {
        when(questionRepository.findById("q1")).thenReturn(Optional.of(ojQuestion()));
        when(submissionRepository.findBySessionQuestion(any(), any())).thenReturn(List.of());
        when(executor.judge(any(JudgeRequest.class))).thenReturn(result(1, 1));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(),
                anyLong(), anyLong(), anyInt(), any())).thenReturn(500);

        processor().processCoding("s1", "q1", "Alice", "uuid-1", "python", "print(3)", Map.of());

        verify(submissionRepository).save(any(Submission.class));
        verify(roomManager).broadcastLeaderboard("s1");
    }

    @Test
    void lowerResubmissionDoesNotReplaceHigherScore() {
        when(questionRepository.findById("q1")).thenReturn(Optional.of(ojQuestion()));
        when(submissionRepository.findBySessionQuestion(any(), any())).thenReturn(List.of());
        when(executor.judge(any(JudgeRequest.class))).thenReturn(result(1, 1));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(),
                anyLong(), anyLong(), anyInt(), any())).thenReturn(100);
        when(submissionRepository.findBest("s1", "q1", "uuid-1"))
                .thenReturn(Optional.of(new Submission("prev", "s1", "q1", "Alice", "uuid-1",
                        "{}", 999, true, "", 1, null)));

        processor().processCoding("s1", "q1", "Alice", "uuid-1", "python", "print(3)", Map.of());

        verify(submissionRepository, never()).save(any(Submission.class));
        verify(roomManager).broadcastLeaderboard("s1");
    }

    // ---------- guards ----------

    @Test
    void misroutedSelectionAnswerIsRejectedNotJudged() {
        String mcqCfg = Json.write(Map.of("correctIndex", 0));
        when(questionRepository.findById("m1")).thenReturn(Optional.of(
                new Question("m1", "qz", "M", "D", "MCQ", null, 30, 100, mcqCfg, 0, null)));

        processor().processCoding("s1", "m1", "Eve", "u-e", "python", "print(1)", Map.of());

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(saved.capture());
        assertEquals(0, saved.getValue().scoreEarned());
        assertTrue(saved.getValue().judgeLog().contains("not_a_coding_question"));
        verify(executor, never()).judge(any());
        verify(roomManager).broadcastLeaderboard("s1");
    }

    @Test
    void oversizedSourceIsRejectedWithoutJudging() {
        when(questionRepository.findById("q1")).thenReturn(Optional.of(ojQuestion()));

        processor().processCoding("s1", "q1", "Big", "u-b", "python", "x".repeat(70_000), Map.of());

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(saved.capture());
        assertEquals(0, saved.getValue().scoreEarned());
        verify(executor, never()).judge(any());
    }

    @Test
    void attemptCapSilentlyDropsFurtherSubmissions() {
        when(questionRepository.findById("q1")).thenReturn(Optional.of(ojQuestion()));
        when(submissionRepository.findBySessionQuestion("s1", "q1")).thenReturn(java.util.List.of(
                new Submission("a", "s1", "q1", "F", "uuid-f", "{}", 10, false, "", 50, null)));

        processor().processCoding("s1", "q1", "Flooder", "uuid-f", "python", "print(1)", Map.of());

        verify(executor, never()).judge(any());
        verify(submissionRepository, never()).save(any(Submission.class));
    }

    @Test
    void compileFailureStillRecordsZeroScoreAttempt() {
        when(questionRepository.findById("q1")).thenReturn(Optional.of(ojQuestion()));
        when(submissionRepository.findBySessionQuestion(any(), any())).thenReturn(List.of());
        when(executor.judge(any(JudgeRequest.class))).thenReturn(result(0, 1));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(),
                anyLong(), anyLong(), anyInt(), any())).thenReturn(0);

        processor().processCoding("s1", "q1", "Newbie", "u-n", "cpp", "int main(", Map.of());

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(saved.capture());
        assertEquals(0, saved.getValue().scoreEarned());
        assertEquals(false, saved.getValue().correct());
    }

    @Test
    void unknownQuestionIdIsIgnored() {
        when(questionRepository.findById("ghost")).thenReturn(Optional.empty());
        processor().processCoding("s1", "ghost", "X", "u-x", "python", "print(1)", Map.of());
        verify(executor, never()).judge(any());
        verify(submissionRepository, never()).save(any(Submission.class));
        verify(roomManager, never()).broadcastLeaderboard(anyString());
    }
}
