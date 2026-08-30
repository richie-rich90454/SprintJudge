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
}
