package com.sprintjudge.service;

import java.util.ArrayList;
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
	 * @param submissions
	 *            submission repository, never null
	 */
	public RoomExportService(SubmissionRepository submissions) {
		this.submissions = submissions;
	}

	/**
	 * Exports one session as CSV with a fixed header. Empty sessions yield the
	 * header only, never null.
	 *
	 * @param sessionId
	 *            game session id, never null
	 * @return CSV text with header plus one row per submission, never null
	 */
	public String exportCsv(String sessionId) {
		StringBuilder out = new StringBuilder("playerUuid,questionId,scoreEarned,correct,attemptCount");
		for (Submission submission : ordered(sessionId)) {
			out.append('\n').append(escape(submission.getPlayerUuid())).append(',')
					.append(escape(submission.getQuestionId())).append(',').append(submission.getScoreEarned())
					.append(',').append(submission.isCorrect()).append(',').append(submission.getAttemptCount());
		}
		return out.toString();
	}

	/**
	 * Exports one session as JSON-ready rows with stable keys.
	 *
	 * @param sessionId
	 *            game session id, never null
	 * @return rows ordered by question then player, never null
	 */
	public List<Map<String, Object>> exportJson(String sessionId) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (Submission submission : ordered(sessionId)) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("playerUuid", submission.getPlayerUuid());
			row.put("questionId", submission.getQuestionId());
			row.put("scoreEarned", submission.getScoreEarned());
			row.put("correct", submission.isCorrect());
			row.put("attemptCount", submission.getAttemptCount());
			rows.add(row);
		}
		return List.copyOf(rows);
	}

	private List<Submission> ordered(String sessionId) {
		List<Submission> rows = new ArrayList<>(submissions.findBySession(sessionId));
		rows.sort(new java.util.Comparator<Submission>() {
			@Override
			public int compare(Submission a, Submission b) {
				int c = (a.getQuestionId() == null ? "" : a.getQuestionId())
						.compareTo(b.getQuestionId() == null ? "" : b.getQuestionId());
				if (c != 0)
					return c;
				String pa = a.getPlayerUuid() == null ? "" : a.getPlayerUuid();
				String pb = b.getPlayerUuid() == null ? "" : b.getPlayerUuid();
				return pa.compareTo(pb);
			}
		});
		return rows;
	}

	private static String escape(String value) {
		String text = value == null ? "" : value;
		// A leading formula marker is executed by Excel when a teacher opens the
		// export; prefix it with a quote so it stays literal text.
		if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0)
			text = "'" + text;
		if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}
}
