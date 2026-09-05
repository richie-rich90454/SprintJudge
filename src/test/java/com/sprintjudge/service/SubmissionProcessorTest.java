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
        lenient().when(q.pointsBase()).thenReturn(100);
        when(q.timeLimitSec()).thenReturn(10);
        lenient().when(q.title()).thenReturn("T");
        lenient().when(q.description()).thenReturn("D");
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
    void questionNotFoundNotifiesWithZero() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        when(questionRepository.findById("q")).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer, never()).offer(any());
        verify(leaderboardBroadcaster, never()).broadcastLeaderboard(anyString());
        assertEquals(0, received[0]);
    }

    @Test
    void nonCodingRejectedWithZero() throws Exception {
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
        assertEquals(0, received[0]); // rejected routing still notifies with zero
    }

    @Test
    void sourceTooLargeRejectedWithZero() throws Exception {
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
        assertEquals(0, received[0]);
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
        assertEquals(0, received[0]); // rejected source still notifies with zero
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

    @Test
    void executorThrowTriggersRejectedWithZero() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenThrow(new RuntimeException("judge crash"));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(writeBuffer, never()).offer(any());
        assertEquals(2, slot.availablePermits());
    }

    @Test
    void aiFeedbackAttachedWhenEnabledAndFailing() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 2, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(20);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new AiGradingService.AiGradeResult(true, "fix the loop", 15, "ok"));
        String[] feedback = {null};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> feedback[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 3L, handler).get());
        assertEquals("fix the loop", feedback[0]);
        verify(writeBuffer).offer(any(Submission.class));
    }

    @Test
    void aiUnavailableYieldsNullFeedback() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 1, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(5);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(AiGradingService.AiGradeResult.unavailable());
        String[] feedback = {"sentinel"};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> feedback[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertNull(feedback[0]);
    }

    @Test
    void aiThrowIsSwallowedAndStillAccepts() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 1, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(7);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("ai down"));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(7, received[0]);
    }

    @Test
    void aiSkippedWhenDisabled() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 1, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(11);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(false);
        String[] feedback = {"sentinel"};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> feedback[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertNull(feedback[0]);
        verify(aiGradingService, never()).grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void aiSkippedWhenAllTestsPass() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(2, 2, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(100);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        String[] feedback = {"sentinel"};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> feedback[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertNull(feedback[0]);
        verify(aiGradingService, never()).grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void equalBestScoreDoesNotOverwrite() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(40);
        Submission best = mock(Submission.class);
        when(best.scoreEarned()).thenReturn(40);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.of(best));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer, never()).offer(any());
        assertEquals(40, received[0]);
    }

    @Test
    void hiddenFlagAndMemoryParsedIntoJudgeRequest() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion(
                "{\"testCases\":[{\"input\":\"a\",\"expectedOutput\":\"b\",\"isHidden\":true},{\"input\":\"c\",\"expectedOutput\":\"d\"}],\"memoryLimitMb\":512}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(2, 2, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(100);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals(2, cap.getValue().testCases().size());
        assertTrue(cap.getValue().testCases().get(0).hidden());
        assertFalse(cap.getValue().testCases().get(1).hidden());
        assertEquals(512, cap.getValue().memoryLimitMb());
        assertEquals(100, received[0]);
    }

    @Test
    void memoryDefaultsAndTimeoutFloorApplied() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question tiny = mock(Question.class);
        when(tiny.questionType()).thenReturn("OJ_FULL");
        when(tiny.config()).thenReturn("{}");
        when(tiny.pointsBase()).thenReturn(100);
        when(tiny.timeLimitSec()).thenReturn(1);
        when(questionRepository.findById("q")).thenReturn(Optional.of(tiny));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(90);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals(256, cap.getValue().memoryLimitMb());
        assertEquals(5, cap.getValue().timeoutSec());
    }

    @Test
    void sourceAtExactLimitIsAccepted() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(60);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "x".repeat(65536), 1, Map.of(), 0L, handler).get());
        assertEquals(60, received[0]);
        verify(executor).judge(any(JudgeRequest.class));
    }

    @Test
    void sourceOneOverLimitIsRejected() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_FULL");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "x".repeat(65537), 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(executor, never()).judge(any());
    }

    @Test
    void zeroTotalTestsScoresZeroButStillNotifies() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 0, false, List.of()));
        when(scoringEngine.scoreCoding(0, 0, 100, false, 0L, 10L, 1, Map.of())).thenReturn(0);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        int[] totals = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> { received[0] = s; totals[0] = total; };
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        assertEquals(0, totals[0]);
    }

    @Test
    void attemptOneForwardsCountToScoringEngine() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(2, 2, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), eq(1), any())).thenReturn(95);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "python", "print(1)", 1, Map.of(), 2L, handler).get());
        assertEquals(95, received[0]);
        verify(scoringEngine).scoreCoding(eq(2), eq(2), eq(100), eq(true), eq(2L), eq(10L), eq(1), any());
    }

    @Test
    void attemptTwoWithJavaPartialPassForwardsTwo() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 3, false, List.of()));
        when(scoringEngine.scoreCoding(eq(1), eq(3), eq(100), eq(false), eq(5L), eq(10L), eq(2), any())).thenReturn(40);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        int[] passed = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, ps, total, ai) -> { received[0] = s; passed[0] = ps; };
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 2, Map.of(), 5L, handler).get());
        assertEquals(40, received[0]);
        assertEquals(1, passed[0]);
    }

    @Test
    void attemptThreeWithSlowSubmitStillScores() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 2, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), eq(120L), anyLong(), eq(3), any())).thenReturn(10);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "python", "code", 3, Map.of(), 120L, handler).get());
        assertEquals(10, received[0]);
        verify(scoringEngine).scoreCoding(eq(0), eq(2), eq(100), eq(false), eq(120L), eq(10L), eq(3), any());
    }

    @Test
    void attemptFourAllPassSkipsAiAndScoresHigh() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(3, 3, true, List.of()));
        when(scoringEngine.scoreCoding(eq(3), eq(3), eq(100), eq(true), anyLong(), anyLong(), eq(4), any())).thenReturn(88);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        String[] feedback = {"sentinel"};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> feedback[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "go", "code", 4, Map.of(), 1L, handler).get());
        assertNull(feedback[0]);
        verify(aiGradingService, never()).grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void attemptFiveWithCustomSettingsMapForwarded() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 2, false, List.of()));
        java.util.Map<String, Object> settings = Map.of("bonus", 5);
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), eq(5), eq(settings))).thenReturn(33);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "rust", "code", 5, settings, 7L, handler).get());
        assertEquals(33, received[0]);
    }

    @Test
    void findBestLowerByOneOverwritesWithOffer() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(51);
        Submission best = mock(Submission.class);
        when(best.scoreEarned()).thenReturn(50);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.of(best));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer).offer(any(Submission.class));
        assertEquals(51, received[0]);
    }

    @Test
    void findBestHigherByOneKeepsOldWithoutOffer() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(49);
        Submission best = mock(Submission.class);
        when(best.scoreEarned()).thenReturn(50);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.of(best));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer, never()).offer(any(Submission.class));
        assertEquals(49, received[0]);
    }

    @Test
    void findBestEqualWithFailingRunDoesNotOverwrite() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 2, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(30);
        Submission best = mock(Submission.class);
        when(best.scoreEarned()).thenReturn(30);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.of(best));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 2, Map.of(), 0L, handler).get());
        verify(writeBuffer, never()).offer(any(Submission.class));
    }

    @Test
    void zeroScoreWithNoBestStillWritesAuditRow() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 2, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(0);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-2};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer).offer(any(Submission.class));
        assertEquals(0, received[0]);
    }

    @Test
    void aiEnabledPythonPartialFailAttachesFeedback() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 3, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(45);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(eq("python"), any(), any(), any(), eq(false), eq(45), anyInt())).thenReturn(new AiGradingService.AiGradeResult(true, "hint-py", 10, "ok"));
        String[] fb = {null};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> fb[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "python", "code", 1, Map.of(), 4L, handler).get());
        assertEquals("hint-py", fb[0]);
    }

    @Test
    void aiEnabledJavaPartialFailAttachesFeedback() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 1, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(12);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(eq("java"), any(), any(), any(), eq(false), eq(12), anyInt())).thenReturn(new AiGradingService.AiGradeResult(true, "hint-java", 5, "ok"));
        String[] fb = {null};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> fb[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 2, Map.of(), 0L, handler).get());
        assertEquals("hint-java", fb[0]);
    }

    @Test
    void executorAllPassThreeOfThreeSkipsAiGrade() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[{},{},{}]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(3, 3, true, List.of()));
        when(scoringEngine.scoreCoding(eq(3), eq(3), eq(100), eq(true), anyLong(), anyLong(), anyInt(), any())).thenReturn(100);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        boolean[] called = {false};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> called[0] = ap;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertTrue(called[0]);
        verify(aiGradingService, never()).grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void executorPartialTwoOfFiveTriggersAiWhenEnabled() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(2, 5, false, List.of()));
        when(scoringEngine.scoreCoding(eq(2), eq(5), eq(100), eq(false), anyLong(), anyLong(), anyInt(), any())).thenReturn(38);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(any(), any(), any(), any(), eq(false), eq(38), anyInt())).thenReturn(new AiGradingService.AiGradeResult(true, "partial-hint", 8, "ok"));
        String[] fb = {null};
        int[] tot = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> { fb[0] = ai; tot[0] = total; };
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals("partial-hint", fb[0]);
        assertEquals(5, tot[0]);
    }

    @Test
    void executorEmptyCasesZeroTotalStillCallsScoring() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 0, true, List.of()));
        when(scoringEngine.scoreCoding(eq(0), eq(0), eq(100), eq(true), anyLong(), anyLong(), eq(1), any())).thenReturn(100);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(100, received[0]);
    }

    @Test
    void saturatedSlotReturnsFalseWithoutTouchingHandlerOrBuffer() throws Exception {
        Semaphore slot = new Semaphore(1);
        slot.acquireUninterruptibly();
        SubmissionProcessor p = processor(slot);
        boolean[] handlerCalled = {false};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> handlerCalled[0] = true;
        assertFalse(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        slot.release();
        assertFalse(handlerCalled[0]);
        verify(writeBuffer, never()).offer(any());
        verify(leaderboardBroadcaster, never()).broadcastLeaderboard(anyString());
    }

    @Test
    void saturatedSlotDoesNotTouchQuestionRepoOrExecutor() throws Exception {
        Semaphore slot = new Semaphore(1);
        slot.acquireUninterruptibly();
        SubmissionProcessor p = processor(slot);
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertFalse(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        slot.release();
        verifyNoInteractions(questionRepository, executor, scoringEngine, submissionRepository, aiGradingService);
    }

    @Test
    void questionDeletedBetweenSubmitAndJudgeNotifiesZeroWithoutOffer() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        when(questionRepository.findById("gone")).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "gone", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(writeBuffer, never()).offer(any());
        verify(executor, never()).judge(any());
    }

    @Test
    void nullSourceWithCodingQuestionRejectedAndBuffered() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_FULL");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "python", null, 3, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(writeBuffer).offer(any(Submission.class));
        verify(executor, never()).judge(any());
    }

    @Test
    void emptySourceAcceptedThroughJudge() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 1, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(5);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "", 1, Map.of(), 0L, handler).get());
        assertEquals(5, received[0]);
        verify(executor).judge(any(JudgeRequest.class));
    }

    @Test
    void oversizedSourceByOneBufferedAsTooLarge() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_FULL");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "y".repeat(65537), 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        org.mockito.ArgumentCaptor<Submission> cap = org.mockito.ArgumentCaptor.forClass(Submission.class);
        verify(writeBuffer).offer(cap.capture());
        assertTrue(cap.getValue().judgeLog().contains("source_too_large"));
    }

    @Test
    void ojPatchTypeAcceptedThroughJudge() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_PATCH");
        when(q.config()).thenReturn("{\"testCases\":[]}");
        when(q.pointsBase()).thenReturn(200);
        when(q.timeLimitSec()).thenReturn(15);
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(eq(1), eq(1), eq(200), eq(true), anyLong(), eq(15L), eq(1), any())).thenReturn(180);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 3L, handler).get());
        assertEquals(180, received[0]);
    }

    @Test
    void trueFalseTypeRejectedWithoutJudge() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("TRUE_FALSE");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(executor, never()).judge(any());
        verify(writeBuffer).offer(any(Submission.class));
    }

    @Test
    void fillBlankTypeRejectedWithAuditRow() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("FILL_BLANK");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<Submission> cap = org.mockito.ArgumentCaptor.forClass(Submission.class);
        verify(writeBuffer).offer(cap.capture());
        assertEquals(0, cap.getValue().scoreEarned());
        assertFalse(cap.getValue().correct());
    }

    @Test
    void languagePythonForwardedToJudgeRequest() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(70);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "python", "print(42)", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals("python", cap.getValue().language());
        assertEquals("print(42)", cap.getValue().sourceCode());
    }

    @Test
    void sourceCodeForwardedVerbatimIncludingNewlines() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(60);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        String src = "def f():\n    return 1\n";
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "python", src, 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals(src, cap.getValue().sourceCode());
    }

    @Test
    void timeoutFloorFiveWhenQuestionLimitOne() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question tiny = mock(Question.class);
        when(tiny.questionType()).thenReturn("OJ_FULL");
        when(tiny.config()).thenReturn("{\"testCases\":[]}");
        when(tiny.pointsBase()).thenReturn(100);
        when(tiny.timeLimitSec()).thenReturn(2);
        when(questionRepository.findById("q")).thenReturn(Optional.of(tiny));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(50);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals(5, cap.getValue().timeoutSec());
    }

    @Test
    void timeoutUsesQuestionLimitThirty() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question big = mock(Question.class);
        when(big.questionType()).thenReturn("OJ_FULL");
        when(big.config()).thenReturn("{\"testCases\":[]}");
        when(big.pointsBase()).thenReturn(100);
        when(big.timeLimitSec()).thenReturn(30);
        when(questionRepository.findById("q")).thenReturn(Optional.of(big));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(90);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals(30, cap.getValue().timeoutSec());
    }

    @Test
    void memoryExplicit512Forwarded() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[],\"memoryLimitMb\":512}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(80);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals(512, cap.getValue().memoryLimitMb());
    }

    @Test
    void memoryDefault256WhenAbsent() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(80);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals(256, cap.getValue().memoryLimitMb());
    }

    @Test
    void hiddenMissingDefaultsFalseForBothCases() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[{\"input\":\"a\",\"expectedOutput\":\"b\"},{\"input\":\"c\",\"expectedOutput\":\"d\"}]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(2, 2, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(100);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals(2, cap.getValue().testCases().size());
        assertFalse(cap.getValue().testCases().get(0).hidden());
        assertFalse(cap.getValue().testCases().get(1).hidden());
    }

    @Test
    void testCaseInputExpectedForwardedExactly() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[{\"input\":\"hello\",\"expectedOutput\":\"world\",\"isHidden\":true}]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(100);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals("hello", cap.getValue().testCases().get(0).input());
        assertEquals("world", cap.getValue().testCases().get(0).expectedOutput());
        assertTrue(cap.getValue().testCases().get(0).hidden());
    }

    @Test
    void scoringReceivesCustomPointsBase250() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_FULL");
        when(q.config()).thenReturn("{\"testCases\":[]}");
        when(q.pointsBase()).thenReturn(250);
        when(q.timeLimitSec()).thenReturn(10);
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(eq(1), eq(1), eq(250), eq(true), anyLong(), eq(10L), eq(1), any())).thenReturn(200);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(200, received[0]);
    }

    @Test
    void scoringReceivesTimeTakenNinetySeconds() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), eq(90L), anyLong(), anyInt(), any())).thenReturn(77);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 90L, handler).get());
        assertEquals(77, received[0]);
    }

    @Test
    void slotPermitReleasedAfterSuccess() throws Exception {
        Semaphore slot = new Semaphore(1);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(60);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(1, slot.availablePermits());
    }

    @Test
    void slotPermitReleasedAfterJudgeThrow() throws Exception {
        Semaphore slot = new Semaphore(1);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenThrow(new RuntimeException("boom"));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(1, slot.availablePermits());
    }

    @Test
    void scoringThrowBecomesRejectedZeroWithoutOffer() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenThrow(new RuntimeException("score boom"));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(writeBuffer, never()).offer(any());
        assertEquals(2, slot.availablePermits());
    }

    @Test
    void findBestLookupUsesSessionQuestionPlayerIds() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("qq")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(55);
        when(submissionRepository.findBest(eq("sess-9"), eq("qq"), eq("uuid-9"))).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("sess-9", "pin", "qq", "name", "uuid-9", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(submissionRepository).findBest("sess-9", "qq", "uuid-9");
    }

    @Test
    void writeBufferOfferCarriesLanguageAndSource() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(66);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "python", "my-src", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<Submission> cap = org.mockito.ArgumentCaptor.forClass(Submission.class);
        verify(writeBuffer).offer(cap.capture());
        assertTrue(cap.getValue().responseData().contains("python"));
        assertTrue(cap.getValue().responseData().contains("my-src"));
        assertEquals(66, cap.getValue().scoreEarned());
    }

    @Test
    void aiGradeReceivesLanguageSourceAndScore() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 2, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(21);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(eq("go"), eq("src-go"), any(), any(), eq(false), eq(21), anyInt())).thenReturn(new AiGradingService.AiGradeResult(true, "go-hint", 7, "ok"));
        String[] fb = {null};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> fb[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "go", "src-go", 1, Map.of(), 0L, handler).get());
        assertEquals("go-hint", fb[0]);
    }

    @Test
    void allFailZeroOfThreeStillNotifiesWithZeroTotal() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 3, false, List.of()));
        when(scoringEngine.scoreCoding(eq(0), eq(3), eq(100), eq(false), anyLong(), anyLong(), eq(1), any())).thenReturn(0);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] passed = {-1};
        int[] total = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, ps, tot, ai) -> { passed[0] = ps; total[0] = tot; };
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, passed[0]);
        assertEquals(3, total[0]);
    }

    @Test
    void numericTypeRejectedWithoutJudge() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("NUMERIC");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "42", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(executor, never()).judge(any());
    }

    @Test
    void leaderboardNeverBroadcastFromProcessor() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(2, 2, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(99);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(leaderboardBroadcaster, never()).broadcastLeaderboard(anyString());
    }

    @Test
    void handlerReceivesAllPassedFlagTrueOnFullPass() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(2, 2, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(90);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        boolean[] flag = {false};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> flag[0] = ap;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertTrue(flag[0]);
    }

    @Test
    void handlerReceivesAllPassedFlagFalseOnPartial() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 2, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(40);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        boolean[] flag = {true};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> flag[0] = ap;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertFalse(flag[0]);
    }

    @Test
    void whitespaceOnlySourceGoesToJudge() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 1, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(3);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "   ", 1, Map.of(), 0L, handler).get());
        assertEquals(3, received[0]);
        verify(executor).judge(any(JudgeRequest.class));
    }

    @Test
    void zeroPermitSlotImmediatelyReturnsFalse() throws Exception {
        Semaphore slot = new Semaphore(0);
        SubmissionProcessor p = processor(slot);
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> { throw new AssertionError("must not be called"); };
        assertFalse(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verifyNoInteractions(questionRepository, executor, writeBuffer);
    }

    @Test
    void aiUnavailableWithAllFailYieldsNullFeedback() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 3, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(9);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt())).thenReturn(AiGradingService.AiGradeResult.unavailable());
        String[] fb = {"sentinel"};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> fb[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "c", "code", 1, Map.of(), 8L, handler).get());
        assertNull(fb[0]);
    }

    @Test
    void aiThrowWithAllFailStillAcceptsScore() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(0, 3, false, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(14);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(aiGradingService.isEnabled()).thenReturn(true);
        when(aiGradingService.grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt())).thenThrow(new RuntimeException("ai down"));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "cpp", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(14, received[0]);
    }

    @Test
    void findBestLookupFailureBecomesRejectedZero() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(80);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("db down"));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(writeBuffer, never()).offer(any());
    }

    @Test
    void rejectedSubmissionCarriesNotACodingQuestionLog() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("MCQ");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<Submission> cap = org.mockito.ArgumentCaptor.forClass(Submission.class);
        verify(writeBuffer).offer(cap.capture());
        assertTrue(cap.getValue().judgeLog().contains("not_a_coding_question"));
    }

    @Test
    void rejectedNullSourceCarriesSourceMissingLog() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_FULL");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", null, 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<Submission> cap = org.mockito.ArgumentCaptor.forClass(Submission.class);
        verify(writeBuffer).offer(cap.capture());
        assertTrue(cap.getValue().judgeLog().contains("source_missing"));
    }

    @Test
    void singleHiddenCaseParsedWithMemoryAndTimeout() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[{\"input\":\"x\",\"expectedOutput\":\"y\",\"isHidden\":true}],\"memoryLimitMb\":1024}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(100);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        org.mockito.ArgumentCaptor<JudgeRequest> cap = org.mockito.ArgumentCaptor.forClass(JudgeRequest.class);
        verify(executor).judge(cap.capture());
        assertEquals(1, cap.getValue().testCases().size());
        assertTrue(cap.getValue().testCases().get(0).hidden());
        assertEquals(1024, cap.getValue().memoryLimitMb());
    }

    @Test
    void questionWithNullConfigRejectedWithoutJudge() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OJ_FULL");
        when(q.config()).thenReturn(null);
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(executor, never()).judge(any());
        verify(writeBuffer, never()).offer(any());
        assertEquals(2, slot.availablePermits());
    }

    @Test
    void attemptZeroForwardedWhenCallerPassesZero() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), eq(0), any())).thenReturn(70);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 0, Map.of(), 0L, handler).get());
        assertEquals(70, received[0]);
    }

    @Test
    void concurrentSlotsBothSucceedWhenTwoPermits() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById(anyString())).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(50);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u1", "java", "c1", 1, Map.of(), 0L, handler).get());
        assertTrue(p.processCoding("s", "pin", "q", "name", "u2", "java", "c2", 1, Map.of(), 0L, handler).get());
        assertEquals(2, slot.availablePermits());
        verify(executor, times(2)).judge(any(JudgeRequest.class));
    }

    @Test
    void outputPredTypeRejectedWithoutJudge() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("OUTPUT_PRED");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(executor, never()).judge(any());
        verify(writeBuffer).offer(any(Submission.class));
    }

    @Test
    void complexityTypeRejectedWithZeroScore() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("COMPLEXITY");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
    }

    @Test
    void dragSortTypeRejectedWithoutScoring() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("DRAG_SORT");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(scoringEngine, never()).scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void clickBugTypeRejectedAndBuffered() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("CLICK_BUG");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(writeBuffer).offer(any(Submission.class));
        verify(leaderboardBroadcaster, never()).broadcastLeaderboard(anyString());
    }

    @Test
    void codeCompletionTypeRejectedWithoutJudge() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("CODE_COMPLETION");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        verify(executor, never()).judge(any());
    }

    @Test
    void multipleSelectTypeRejectedAsNonCoding() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("MULTIPLE_SELECT");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(writeBuffer).offer(any(Submission.class));
    }

    @Test
    void aiGradeSkippedWhenQuestionTitleEmptyAndAllPass() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(95);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        String[] fb = {"x"};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> fb[0] = ai;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertNull(fb[0]);
        verify(aiGradingService, never()).grade(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void largeSourceAtLimitMinusOneAccepted() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 1, true, List.of()));
        when(scoringEngine.scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any())).thenReturn(61);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "z".repeat(65535), 1, Map.of(), 0L, handler).get());
        assertEquals(61, received[0]);
    }

    @Test
    void judgeResultDetailsListIgnoredForScoring() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        var details = List.of(new JudgeResult.CaseResult(0, true, "2", "2", null), new JudgeResult.CaseResult(1, false, "3", "4", "wa"));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(1, 2, false, details));
        when(scoringEngine.scoreCoding(eq(1), eq(2), eq(100), eq(false), anyLong(), anyLong(), eq(1), any())).thenReturn(44);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(44, received[0]);
    }

    @Test
    void bestNullWithHighScoreWritesAndNotifiesHigh() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = codingQuestion("{\"testCases\":[]}");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        when(executor.judge(any(JudgeRequest.class))).thenReturn(new JudgeResult(5, 5, true, List.of()));
        when(scoringEngine.scoreCoding(eq(5), eq(5), eq(100), eq(true), anyLong(), anyLong(), eq(1), any())).thenReturn(250);
        when(submissionRepository.findBest(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        int[] received = {-1};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "code", 1, Map.of(), 0L, handler).get());
        assertEquals(250, received[0]);
        verify(writeBuffer).offer(any(Submission.class));
    }

    @Test
    void fillBlankCodingPathStillRejectedAsNonCoding() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("FILL_BLANK");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> {};
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "answer", 1, Map.of(), 0L, handler).get());
        verify(executor, never()).judge(any());
        verify(writeBuffer).offer(any(Submission.class));
    }

    @Test
    void trueFalseCodingPathRejectedWithZeroNotify() throws Exception {
        Semaphore slot = new Semaphore(2);
        SubmissionProcessor p = processor(slot);
        Question q = mock(Question.class);
        when(q.questionType()).thenReturn("TRUE_FALSE");
        when(questionRepository.findById("q")).thenReturn(Optional.of(q));
        int[] received = {-5};
        CodingOutcomeConsumer handler = (u, s, ap, passed, total, ai) -> received[0] = s;
        assertTrue(p.processCoding("s", "pin", "q", "name", "u", "java", "true", 1, Map.of(), 0L, handler).get());
        assertEquals(0, received[0]);
        verify(scoringEngine, never()).scoreCoding(anyInt(), anyInt(), anyInt(), anyBoolean(), anyLong(), anyLong(), anyInt(), any());
    }
}
