package com.sprintjudge.service;

import com.sprintjudge.domain.enums.QuestionType;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.service.executor.CodeExecutor;
import com.sprintjudge.service.executor.JudgeRequest;
import com.sprintjudge.service.executor.JudgeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionProcessorTest {

    @Mock CodeExecutor executor;
    @Mock SubmissionRepository submissionRepository;
    @Mock QuestionRepository questionRepository;
    @Mock ScoringEngine scoringEngine;
    @Mock LeaderboardBroadcaster leaderboardBroadcaster;
    @Mock SubmissionWriteBuffer writeBuffer;
    @Mock AiGradingService aiGradingService;

    private SubmissionProcessor processor(Semaphore slot) {
        return new SubmissionProcessor(executor, submissionRepository, questionRepository,
                scoringEngine, leaderboardBroadcaster, writeBuffer, aiGradingService, slot);
    }

    private Question codingQuestion(String config) {
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_FULL");
        when(q.config()).thenReturn(config);
        when(q.pointsBase()).thenReturn(100);
        when(q.timeLimitSec()).thenReturn(10);
        return q;
    }

    @Test
    void saturatedReturnsFalse() throws Exception {
        Semaphore slot = new Semaphore(1);
        slot.acquireUninterruptibly();
        SubmissionProcessor p = processor(slot);
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertFalse(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        slot.release();
        verifyNoInteractions(executor);
        assertEquals(-1, received[0]);
    }

    @Test
    void questionNotFoundReturnsTrueButNoCallback() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        when(questionRepository.findById("q")).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer, never()).offer(any());
        verify(leaderboardBroadcaster, never()).broadcastLeaderboard(anyString());
        assertEquals(-1, received[0]);
    }

    @Test
    void nonCodingRejectedSilently() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("MCQ");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer).offer(any());
        verify(executor, never()).judge(any());
        verify(leaderboardBroadcaster, never()).broadcastLeaderboard(anyString());
        assertEquals(-1, received[0]); // handler is not invoked for rejected routing
    }

    @Test
    void sourceTooLargeRejected() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_FULL");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "x".repeat(70000), 1, Map.of(), 0L, handler).get());
        verify(writeBuffer).offer(any());
        verify(executor, never()).judge(any());
        assertEquals(-1, received[0]);
    }

    @Test
    void normalCodingFlowWritesWithoutBroadcast() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion(
                "{\"testCases\":[{\"input\":\"1\",\"expectedOutput\":\"2\",\"isHidden\":false}],\"memoryLimitMb\":128,\"timeLimitSec\":10}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        JudgeResult jr = new JudgeResult(1, 1, true, List.of());
        when(executor.judge(any(JudgeRequest.class))).thenReturn(jr);
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(90);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer).offer(any(Submission.class));
        verify(leaderboardBroadcaster, never()).broadcastLeaderboard(anyString());
        assertEquals(90, received[0]);
    }

    @Test
    void bestScoreNotOverwritten() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        JudgeResult jr = new JudgeResult(0, 1, false, List.of());
        when(executor.judge(any(JudgeRequest.class))).thenReturn(jr);
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(10);
        Submission best = mock(Submission.class);
        when(best.scoreEarned()).thenReturn(50);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.of(best));

        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer, never()).offer(any(Submission.class));
        assertEquals(10, received[0]);
    }

    @Test
    void nullSourceCodeIsRejected() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_FULL");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", null, 1, Map.of(), 0L, handler).get());
        verify(executor, never()).judge(any(JudgeRequest.class));
        verify(writeBuffer).offer(any());
        verify(leaderboardBroadcaster, never()).broadcastLeaderboard(anyString());
        assertEquals(-1, received[0]); // handler not invoked for rejected source
    }

    @Test
    void codingConfigWithoutTestCases() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        // config has no "testCases" key -> exercises the has() false branch
        Question q = codingQuestion("{\"memoryLimitMb\":128,\"timeLimitSec\":10}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        JudgeResult jr = new JudgeResult(0, 0, false, List.of());
        when(executor.judge(any(JudgeRequest.class))).thenReturn(jr);
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(0);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
    }

    @Test
    void betterScoreOverwritesBest() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        JudgeResult jr = new JudgeResult(1, 1, true, List.of());
        when(executor.judge(any(JudgeRequest.class))).thenReturn(jr);
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(50);
        Submission best = mock(Submission.class);
        when(best.scoreEarned()).thenReturn(10); // existing best is lower -> overwrite
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.of(best));

        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer).offer(any(Submission.class)); // 50 > 10 so it overwrites
        assertEquals(50, received[0]);
    }
}
