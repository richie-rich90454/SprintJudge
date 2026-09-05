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
}
