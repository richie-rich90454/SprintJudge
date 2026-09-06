package com.sprintjudge.websocket;

import com.sprintjudge.domain.models.Question;
import com.sprintjudge.domain.models.Quiz;
import com.sprintjudge.repository.QuestionRepository;
import com.sprintjudge.repository.QuizRepository;
import com.sprintjudge.util.Json;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full solo-practice journeys through the real stack (HTTP + WebSocket +
 * SQLite): create a practice room on a chosen bank, join as a player, and
 * prove Q1 starts streaming without any host. Guards the exact symptom of a
 * player stranded on "Waiting for the host to start the next question".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PracticeSoloFlowTest {

    // Fixed scratch path (NOT @TempDir): on Windows the SQLite pool still
    // holds the file when JUnit scrubs temp dirs, failing teardown.
    private static boolean cleaned;

    static synchronized Path dbFile() {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "oq-e2e");
            Files.createDirectories(dir);
            // The registry supplier may run more than once: wipe only first.
            if (!cleaned) {
                for (String suffix : new String[]{"", "-wal", "-shm", "-journal"}) {
                    Files.deleteIfExists(dir.resolve("solo-flow.db" + suffix));
                }
                cleaned = true;
            }
            return dir.resolve("solo-flow.db");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("sprintjudge.db.path", () -> dbFile().toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    QuizRepository quizzes;

    @Autowired
    QuestionRepository questions;

    @LocalServerPort
    int port;

    /** Per-connection inbox: accumulates text frames into whole messages. */
    static final class Inbox {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        final StringBuilder fragment = new StringBuilder();
        final List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();

        WebSocket.Listener listener() {
            return new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    webSocket.request(Long.MAX_VALUE);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    fragment.append(data);
                    if (last) {
                        queue.offer(fragment.toString());
                        seen.add(fragment.toString());
                        fragment.setLength(0);
                    }
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    queue.offer("WS-ERROR: " + error);
                }
            };
        }
        /** Next message matching all needles, failing fast on rejects. */
        String await(String test, String... needles) {
            long deadline = System.currentTimeMillis() + 15_000;
            while (true) {
                String msg = next(test, deadline);
                if (msg == null) {
                    throw new AssertionError(test + " timed out. seen=" + seen);
                }
                boolean hit = true;
                for (String needle : needles) {
                    if (!msg.contains(needle)) {
                        hit = false;
                        break;
                    }
                }
                if (hit) return msg;
            }
        }

        /** Next raw message or null on timeout, failing fast on rejects. */
        String next(String test, long deadline) {
            try {
                while (System.currentTimeMillis() < deadline) {
                    String msg = queue.poll(Math.max(1, deadline - System.currentTimeMillis()),
                            TimeUnit.MILLISECONDS);
                    if (msg == null) break;
                    if (msg.startsWith("WS-ERROR")) fail(test + ": " + msg);
                    if (msg.contains("\"ERROR\"")) fail(test + " rejected: " + msg);
                    return msg;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(test + " interrupted");
            }
            return null;
        }
    }

    private void seedBank(String quizId) {
        quizzes.create(new Quiz(quizId, "Flow Bank " + quizId, null, null, Instant.now(), true));
        questions.save(new Question("q1-" + quizId, quizId, "Q1", "D", "MCQ", null, 30, 100,
                "{\"options\":[\"a\",\"b\"],\"correctIndex\":0}", 0, Instant.now()));
    }

    private String practicePin(String quizId) throws Exception {
        String body = mvc.perform(post("/api/public/practice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quizId\":\"" + quizId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Object pinCode = Json.readMap(body).get("pinCode");
        assertNotNull(pinCode);
        assertEquals(6, pinCode.toString().length());
        return pinCode.toString();
    }

    private WebSocket openSocket(Inbox inbox) {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), inbox.listener()).join();
    }

    private static String field(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\"([^\"]+)\"").matcher(json);
        if (m.find()) return m.group(1);
        Matcher n = Pattern.compile("\"" + key + "\":([0-9]+)").matcher(json);
        assertTrue(n.find(), "missing " + key + " in " + json);
        return n.group(1);
    }

    @Test
    void soloPracticeJoinsAndReceivesFirstQuestion() throws Exception {
        seedBank("flow-qz");
        String pin = practicePin("flow-qz");
        Inbox inbox = new Inbox();
        WebSocket ws = openSocket(inbox);
        try {
            ws.sendText("{\"type\":\"JOIN\",\"pin\":\"" + pin + "\",\"name\":\"Solo\",\"role\":\"player\"}",
                    true);
            // Ordering contract the client relies on: autostart broadcasts Q1
            // BEFORE the JOINED ack, so the ack must never clear live state.
            String start = inbox.await("Q1", "QUESTION_START", "q1-flow-qz");
            assertNotNull(start, "Q1 QUESTION_START never arrived within 15s");
            String joined = inbox.await("JOINED", "\"JOINED\"");
            assertNotNull(joined, "JOINED never arrived. seen=" + inbox.seen);
        } finally {
            ws.abort();
        }
    }

    @Test
    void rapidDoubleJoinStillStreamsSecondRoom() throws Exception {
        seedBank("flow-qz-2");
        String pinA = practicePin("flow-qz-2");
        String pinB = practicePin("flow-qz-2");
        assertNotEquals(pinA, pinB);
        Inbox inbox = new Inbox();
        WebSocket ws = openSocket(inbox);
        try {
            // Impatient double-Start: two rooms, two seats, one socket. Each
            // room streams its own Q1 BEFORE its JOINED ack.
            ws.sendText("{\"type\":\"JOIN\",\"pin\":\"" + pinA + "\",\"name\":\"Solo\",\"role\":\"player\"}",
                    true);
            ws.sendText("{\"type\":\"JOIN\",\"pin\":\"" + pinB + "\",\"name\":\"Solo\",\"role\":\"player\"}",
                    true);
            // Impatient double-Start: two rooms, two seats, one socket. Streams
            // interleave and catch-ups duplicate Q1s, so count distinct rounds
            // (by round-start instant) and distinct seats.
            int qRounds = 0;
            java.util.Set<String> rounds = new java.util.HashSet<>();
            java.util.Set<String> uuids = new java.util.HashSet<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline && (qRounds < 2 || uuids.size() < 2)) {
                String msg = inbox.next("double join", deadline);
                if (msg == null) break;
                if (msg.contains("QUESTION_START") && msg.contains("q1-flow-qz-2")) {
                    rounds.add(field(msg, "startedAtEpochMs"));
                    qRounds = rounds.size();
                }
                if (msg.contains("\"JOINED\"")) uuids.add(field(msg, "uuid"));
            }
            assertEquals(2, qRounds, "both rooms must stream Q1. seen=" + inbox.seen);
            assertEquals(2, uuids.size(), "both joins must ack distinct seats. seen=" + inbox.seen);
        } finally {
            ws.abort();
        }
    }

    @Test
    void tokenReconnectReclaimsSameSeat() throws Exception {
        seedBank("flow-qz-3");
        String pin = practicePin("flow-qz-3");
        Inbox inbox = new Inbox();
        WebSocket ws = openSocket(inbox);
        String uuid;
        String token;
        try {
            ws.sendText("{\"type\":\"JOIN\",\"pin\":\"" + pin + "\",\"name\":\"Solo\",\"role\":\"player\"}",
                    true);
            String start = inbox.await("Q1", "QUESTION_START", "q1-flow-qz-3");
            assertNotNull(start, "Q1 never arrived before reconnect. seen=" + inbox.seen);
            String joined = inbox.await("join", "\"JOINED\"");
            assertNotNull(joined, "JOINED never arrived. seen=" + inbox.seen);
            uuid = field(joined, "uuid");
            token = field(joined, "rejoinToken");
        } finally {
            ws.abort();
        }
        // Fresh socket, same seat via the rejoin token.
        Inbox inbox2 = new Inbox();
        WebSocket ws2 = openSocket(inbox2);
        try {
            ws2.sendText("{\"type\":\"JOIN\",\"pin\":\"" + pin
                            + "\",\"name\":\"Solo\",\"role\":\"player\",\"rejoinToken\":\"" + token + "\"}",
                    true);
            String rejoined = inbox2.await("rejoin", "\"JOINED\"");
            assertNotNull(rejoined, "rejoin JOINED never arrived");
            assertEquals(uuid, field(rejoined, "uuid"), "reconnect must reclaim the same seat");
        } finally {
            ws2.abort();
        }
    }

    @Test
    void soloJourneyRunsToResultsWithoutHost() throws Exception {
        quizzes.create(new Quiz("flow-full", "Flow Full", null, null, Instant.now(), true));
        questions.save(new Question("q1-flow-full", "flow-full", "Q1", "D", "MCQ", null, 30, 100,
                "{\"options\":[\"a\",\"b\"],\"correctIndex\":0}", 0, Instant.now()));
        questions.save(new Question("q2-flow-full", "flow-full", "Q2", "D", "MCQ", null, 30, 100,
                "{\"options\":[\"a\",\"b\"],\"correctIndex\":1}", 1, Instant.now()));
        String pin = practicePin("flow-full");
        Inbox inbox = new Inbox();
        WebSocket ws = openSocket(inbox);
        try {
            ws.sendText("{\"type\":\"JOIN\",\"pin\":\"" + pin + "\",\"name\":\"Solo\",\"role\":\"player\"}",
                    true);
            String q1 = inbox.await("Q1", "QUESTION_START", "q1-flow-full");
            assertNotNull(q1, "Q1 never arrived. seen=" + inbox.seen);
            String joined = inbox.await("join", "\"JOINED\"");
            String uuid = field(joined, "uuid");

            ws.sendText("{\"type\":\"SUBMIT\",\"questionId\":\"q1-flow-full\",\"response\":"
                            + "{\"selectedIndex\":0},\"language\":\"python\"}",
                    true);
            String feedback = inbox.await("feedback", "SUBMISSION_RESULT", "q1-flow-full");
            assertNotNull(feedback, "submit feedback never arrived. seen=" + inbox.seen);
            // Review then auto-advance on the real 2s timer: no host involved.
            String q2 = inbox.await("Q2", "QUESTION_START", "q2-flow-full");
            assertNotNull(q2, "practice never advanced to Q2. seen=" + inbox.seen);

            ws.sendText("{\"type\":\"SUBMIT\",\"questionId\":\"q2-flow-full\",\"response\":"
                            + "{\"selectedIndex\":1},\"language\":\"python\"}",
                    true);
            String end = inbox.await("end", "GAME_REVIEW");
            assertNotNull(end, "game never ended after the last question. seen=" + inbox.seen);
        } finally {
            ws.abort();
        }
    }
}
