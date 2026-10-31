package com.sprintjudge.service;

import com.sprintjudge.bank.BankRecordSource;
import com.sprintjudge.domain.models.GameSession;
import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Submission;
import com.sprintjudge.repository.GameSessionRepository;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.SubmissionRepository;
import com.sprintjudge.util.Json;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class TeachNextService {

    public record Recommendation(String subject, String unit, String topic, int count) {}

    private final GameSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final BankRecordSource bankRecords;

    public TeachNextService(GameSessionRepository sessionRepository,
                            QuestionRepository questionRepository,
                            SubmissionRepository submissionRepository,
                            BankRecordSource bankRecords) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.bankRecords = bankRecords;
    }

    public Recommendation recommendPractice(String sessionId) {
        GameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown session"));
        List<Question> questions = questionRepository.findByQuiz(session.quizId());
        List<Submission> submissions = submissionRepository.findBySession(sessionId);

        String topic = weakestTopic(questions, submissions);

        String bestSubject = "";
        String bestUnit = "";
        int bestMatches = 0;
        for (String candidateSubject : bankRecords.subjects()) {
            for (String candidateUnit : unitsOf(candidateSubject)) {
                int matches = topicMatches(candidateSubject, candidateUnit, topic);
                boolean better = matches > bestMatches
                        || (matches == bestMatches && matches > 0
                            && "JAVA_PROGRAMMING".equals(candidateSubject)
                            && !"JAVA_PROGRAMMING".equals(bestSubject));
                if (better) {
                    bestMatches = matches;
                    bestSubject = candidateSubject;
                    bestUnit = candidateUnit;
                }
            }
        }

        if (bestMatches == 0) {
            return new Recommendation("", "", topic, 0);
        }

        int available = bankRecords.records(bestSubject, bestUnit.isBlank() ? null : bestUnit).size();
        int count = Math.min(10, available);
        return new Recommendation(bestSubject, bestUnit, topic, count);
    }

    private String weakestTopic(List<Question> questions, List<Submission> submissions) {
        if (questions == null || questions.isEmpty()) return "";
        Map<String, Integer> totals = new HashMap<>();
        Map<String, Integer> corrects = new HashMap<>();
        for (Submission s : submissions) {
            totals.merge(s.questionId(), 1, Integer::sum);
            if (s.correct()) corrects.merge(s.questionId(), 1, Integer::sum);
        }
        // group questions by topic
        Map<String, List<Question>> byTopic = new HashMap<>();
        for (Question q : questions) {
            byTopic.computeIfAbsent(topicOf(q), k -> new ArrayList<>()).add(q);
        }
        String weakest = null;
        double weakestRate = 2.0;
        for (Map.Entry<String, List<Question>> e : byTopic.entrySet()) {
            int total = 0, correct = 0;
            for (Question q : e.getValue()) {
                int t = totals.getOrDefault(q.id(), 0);
                int c = corrects.getOrDefault(q.id(), 0);
                total += t;
                correct += c;
            }
            double rate = total > 0 ? (double) correct / total : 0.0;
            // topics with no submissions are treated as not-yet-attempted (rate 0)
            // but only considered when every topic has submissions; handled by fallback below
            if (rate < weakestRate) {
                weakestRate = rate;
                weakest = e.getKey();
            }
        }
        if (weakest == null) weakest = topicOf(questions.get(0));
        // if no submissions at all, use the first question's topic
        if (submissions == null || submissions.isEmpty()) {
            weakest = topicOf(questions.get(0));
        }
        return weakest == null ? "" : weakest;
    }

    private List<String> unitsOf(String subject) {
        // distinct units present in the bank for this subject
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.TreeSet<>();
        for (Map<String, Object> record : bankRecords.records(subject, null)) {
            String u = text(record, "unit");
            if (!u.isBlank() && seen.add(u)) out.add(u);
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    private int topicMatches(String subject, String unit, String topic) {
        if (topic == null || topic.isBlank()) return 0;
        int count = 0;
        for (Map<String, Object> record : bankRecords.records(subject, unit.isBlank() ? null : unit)) {
            String recTopic = text(record, "topic");
            if (containsIgnoreCase(recTopic, topic) || containsIgnoreCase(topic, recTopic)) {
                count++;
            }
        }
        return count;
    }

    private static String topicOf(Question q) {
        if (q.config() != null && !q.config().isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = Json.readTree(q.config());
                if (node != null && node.has("topic")) {
                    String t = node.get("topic").asText("");
                    if (!t.isBlank()) return t;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return q.title() == null ? "" : q.title();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (needle == null || needle.isBlank()) return false;
        if (haystack == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String text(Map<String, Object> record, String key) {
        Object value = record.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}