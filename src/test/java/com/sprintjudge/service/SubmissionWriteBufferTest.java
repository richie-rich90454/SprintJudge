package com.sprintjudge.service;

import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionWriteBufferTest {

    @Mock SubmissionRepository repository;

    @Test
    void flushEmptyReturnsZero() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 100);
        assertEquals(0, b.flush());
        assertEquals(0, b.depth());
        assertEquals(0, b.offeredTotal());
        assertEquals(0, b.flushedTotal());
    }

    @Test
    void offerAndFlushSavesBatch() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 100);
        Submission s = mock(Submission.class);
        assertTrue(b.offer(s));
        assertEquals(1, b.depth());
        assertEquals(1, b.offeredTotal());
        assertEquals(1, b.flush());
        ArgumentCaptor<List<Submission>> cap = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(cap.capture());
        assertEquals(1, cap.getValue().size());
        assertEquals(1, b.flushedTotal());
        assertEquals(0, b.depth());
    }

    @Test
    void shutdownFlushesRemaining() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 100);
        b.offer(mock(Submission.class));
        b.shutdown();
        verify(repository).saveAll(argThat(list -> !list.isEmpty()));
    }

    @Test
    void flushSafelySwallowsRepositoryError() {
        doThrow(new RuntimeException("boom")).when(repository).saveAll(any());
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 100);
        b.offer(mock(Submission.class));
        b.shutdown(); // flushSafely -> flush -> saveAll throws -> caught
    }

    @Test
    void offerReturnsFalseWhenQueueFull() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 2);
        assertTrue(b.offer(mock(Submission.class)));
        assertTrue(b.offer(mock(Submission.class)));
        assertFalse(b.offer(mock(Submission.class)));
        assertEquals(2, b.depth());
        assertEquals(3, b.offeredTotal());
        b.shutdown();
    }

    @Test
    void flushFailureRequeuesBatchAndRethrows() {
        doThrow(new RuntimeException("db down")).when(repository).saveAll(any());
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        Submission s1 = mock(Submission.class);
        Submission s2 = mock(Submission.class);
        b.offer(s1);
        b.offer(s2);
        assertThrows(RuntimeException.class, b::flush);
        assertEquals(2, b.depth());
        assertEquals(0, b.flushedTotal());
        b.shutdown();
    }

    @Test
    void flushAfterFailureSucceedsOnRetry() {
        doThrow(new RuntimeException("once")).doNothing().when(repository).saveAll(any());
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        b.offer(mock(Submission.class));
        assertThrows(RuntimeException.class, b::flush);
        assertEquals(1, b.depth());
        assertEquals(1, b.flush());
        assertEquals(0, b.depth());
        assertEquals(1, b.flushedTotal());
        b.shutdown();
    }

    @Test
    void flushFailureWithContendedQueueDropsOverflow() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 1);
        Submission original = mock(Submission.class);
        Submission filler = mock(Submission.class);
        b.offer(original);
        doAnswer(inv -> {
            b.offer(filler);
            throw new RuntimeException("db down");
        }).when(repository).saveAll(any());
        assertThrows(RuntimeException.class, b::flush);
        assertEquals(1, b.depth());
        b.shutdown();
    }

    @Test
    void lastFlushStampsAfterSuccessfulFlush() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        assertEquals(0, b.lastFlushEpochMs());
        b.offer(mock(Submission.class));
        long before = System.currentTimeMillis();
        b.flush();
        assertTrue(b.lastFlushEpochMs() >= before);
        b.shutdown();
    }

    @Test
    void shutdownWithEmptyQueueDoesNotTouchRepository() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        b.shutdown();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void offeredAndFlushedTotalsTrackAcrossBatches() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        b.offer(mock(Submission.class));
        b.offer(mock(Submission.class));
        assertEquals(2, b.offeredTotal());
        assertEquals(2, b.flush());
        b.offer(mock(Submission.class));
        assertEquals(3, b.offeredTotal());
        assertEquals(1, b.flush());
        assertEquals(3, b.flushedTotal());
        b.shutdown();
    }
}
