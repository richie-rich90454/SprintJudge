package com.sprintjudge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.SubmissionRepository;

/** Ordering, escaping, and shape for the teacher export. */
@ExtendWith(MockitoExtension.class)
class RoomExportServiceTest {

    @Mock
    SubmissionRepository submissions;

    @InjectMocks
    RoomExportService exporter;

    private static Submission row(String question, String player, int score, boolean correct, int attempts) {
        return new Submission("id-" + question + "-" + player, "s1", question, player, player, "{}",
                score, correct, "", attempts, Instant.EPOCH);
    }

    @Test
    void emptySessionYieldsHeaderOnly() {
        when(submissions.findBySession("s1")).thenReturn(List.of());
        assertEquals("playerUuid,questionId,scoreEarned,correct,attemptCount", exporter.exportCsv("s1"));
        assertEquals(List.of(), exporter.exportJson("s1"));
    }

    @Test
    void rowsOrderByQuestionThenPlayer() {
        when(submissions.findBySession("s1")).thenReturn(
                List.of(row("q2", "b", 5, true, 1), row("q1", "b", 7, false, 2), row("q1", "a", 9, true, 1)));
        assertEquals("playerUuid,questionId,scoreEarned,correct,attemptCount\n"
                + "a,q1,9,true,1\nb,q1,7,false,2\nb,q2,5,true,1", exporter.exportCsv("s1"));
        List<Map<String, Object>> json = exporter.exportJson("s1");
        assertEquals("q1", json.get(0).get("questionId"));
        assertEquals("a", json.get(0).get("playerUuid"));
        assertEquals(9, json.get(0).get("scoreEarned"));
        assertEquals(true, json.get(0).get("correct"));
        assertEquals(1, json.get(0).get("attemptCount"));
    }

    @Test
    void csvEscapesCommasQuotesNewlinesAndNulls() {
        Submission tricky = new Submission("id", "s1", "q,1", "Ann \"Ace\"\nLee", "u,1", "{}",
                3, false, "", 1, Instant.EPOCH);
        Submission nullish = new Submission("id2", "s1", null, null, null, "{}",
                0, false, "", 0, Instant.EPOCH);
        when(submissions.findBySession("s1")).thenReturn(List.of(tricky, nullish));
        assertEquals("playerUuid,questionId,scoreEarned,correct,attemptCount\n"
                + ",,0,false,0\n\"u,1\",\"q,1\",3,false,1", exporter.exportCsv("s1"));
    }
}
