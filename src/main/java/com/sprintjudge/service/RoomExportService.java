package com.sprintjudge.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.SubmissionRepository;

/**
 * Teacher analytics export for one room session. Rows come from stored
 * submissions, ordered by question then player, with minimal CSV escaping.
 */
@Service
public class RoomExportService {

    private final SubmissionRepository submissions;

    /**
     * Creates the exporter over the submission store.
     *
     * @param submissions submission repository, never null
     */
    public RoomExportService(SubmissionRepository submissions) {
        this.submissions = submissions;
    }

    /**
     * Exports one session as CSV with a fixed header. Empty sessions yield
     * the header only, never null.
     *
     * @param sessionId game session id, never null
     * @return CSV text with header plus one row per submission, never null
     */
    public String exportCsv(String sessionId) {
        StringBuilder out = new StringBuilder("playerUuid,questionId,scoreEarned,correct,attemptCount");
        for (Submission submission : ordered(sessionId)) {
            out.append('\n').append(escape(submission.playerUuid())).append(',')
                    .append(escape(submission.questionId())).append(',').append(submission.scoreEarned())
                    .append(',').append(submission.correct()).append(',').append(submission.attemptCount());
        }
        return out.toString();
    }

    /**
     * Exports one session as JSON-ready rows with stable keys.
     *
     * @param sessionId game session id, never null
     * @return rows ordered by question then player, never null
     */
    public List<Map<String, Object>> exportJson(String sessionId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Submission submission : ordered(sessionId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerUuid", submission.playerUuid());
            row.put("questionId", submission.questionId());
            row.put("scoreEarned", submission.scoreEarned());
            row.put("correct", submission.correct());
            row.put("attemptCount", submission.attemptCount());
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private List<Submission> ordered(String sessionId) {
        List<Submission> rows = new ArrayList<>(submissions.findBySession(sessionId));
        rows.sort(Comparator.comparing(Submission::questionId).thenComparing(Submission::playerUuid));
        return rows;
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
