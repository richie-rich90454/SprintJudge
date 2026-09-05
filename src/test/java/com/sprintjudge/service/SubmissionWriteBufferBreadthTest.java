package com.sprintjudge.service;

import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubmissionWriteBufferBreadthTest {

    @Mock SubmissionRepository repository;

    private Submission sub(String id) {
        return new Submission(id, "s", "q", "p", "u", "{}", 1, true, null, 1, Instant.now());
    }

    @Test
    void offerNullThrows() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        try {
            assertThrows(NullPointerException.class, () -> b.offer(null));
        } finally {
            b.shutdown();
        }
    }

    @Test
    void failedFlushDoesNotStampTime() {
        doThrow(new RuntimeException("down")).when(repository).saveAll(any());
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        try {
            b.offer(sub("a"));
            try {
                b.flush();
            } catch (RuntimeException ignored) {
            }
            assertEquals(0, b.lastFlushEpochMs());
            assertEquals(0, b.flushedTotal());
            assertEquals(1, b.depth());
        } finally {
            b.shutdown();
        }
    }

    @Test
    void flushPreservesInsertionOrder() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        try {
            b.offer(sub("first"));
            b.offer(sub("second"));
            b.offer(sub("third"));
            assertEquals(3, b.flush());
            ArgumentCaptor<List<Submission>> cap = ArgumentCaptor.forClass(List.class);
            verify(repository).saveAll(cap.capture());
            assertEquals("first", cap.getValue().get(0).id());
            assertEquals("second", cap.getValue().get(1).id());
            assertEquals("third", cap.getValue().get(2).id());
        } finally {
            b.shutdown();
        }
    }

    @Test
    void requeuePreservesOrderOnRetry() {
        doThrow(new RuntimeException("once")).doNothing().when(repository).saveAll(any());
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        try {
            b.offer(sub("one"));
            b.offer(sub("two"));
            try {
                b.flush();
            } catch (RuntimeException ignored) {
            }
            assertEquals(2, b.flush());
            ArgumentCaptor<List<Submission>> cap = ArgumentCaptor.forClass(List.class);
            verify(repository, times(2)).saveAll(cap.capture());
            List<Submission> retried = cap.getAllValues().get(1);
            assertEquals("one", retried.get(0).id());
            assertEquals("two", retried.get(1).id());
        } finally {
            b.shutdown();
        }
    }

    @Test
    void flushWorksAfterShutdown() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        b.offer(sub("a"));
        b.shutdown();
        b.offer(sub("b"));
        assertEquals(1, b.flush());
        assertEquals(2, b.flushedTotal());
        b.shutdown();
    }

    @Test
    void shutdownTwiceIsSafe() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        b.offer(sub("a"));
        b.shutdown();
        b.shutdown();
        assertEquals(1, b.flushedTotal());
    }

    @Test
    void fiveOffersFlushAsOneBatch() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 10);
        try {
            for (int i = 0; i < 5; i++) b.offer(sub("s" + i));
            assertEquals(5, b.offeredTotal());
            assertEquals(5, b.flush());
            assertEquals(0, b.depth());
            assertEquals(5, b.flushedTotal());
            assertTrue(b.lastFlushEpochMs() > 0);
        } finally {
            b.shutdown();
        }
    }

    @Test
    void rejectedOffersStillCountTowardOfferedTotal() {
        SubmissionWriteBuffer b = new SubmissionWriteBuffer(repository, 1_000_000L, 1);
        try {
            b.offer(sub("a"));
            b.offer(sub("b"));
            assertEquals(2, b.offeredTotal());
            assertEquals(1, b.depth());
            assertEquals(0, b.flushedTotal());
        } finally {
            b.shutdown();
        }
    }
}
