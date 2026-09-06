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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full solo-practice journey through the real stack (HTTP + WebSocket +
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

    @Test
    void soloPracticeJoinsAndReceivesFirstQuestion() throws Exception {
        quizzes.create(new Quiz("flow-qz", "Flow Bank", null, null, Instant.now(), true));
        questions.save(new Question("flow-q1", "flow-qz", "Q1", "D", "MCQ", null, 30, 100,
                "{\"options\":[\"a\",\"b\"],\"correctIndex\":0}", 0, Instant.now()));

        String body = mvc.perform(post("/api/public/practice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quizId\":\"flow-qz\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Object pinCode = Json.readMap(body).get("pinCode");
        assertNotNull(pinCode);
        assertEquals(6, pinCode.toString().length());

        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        StringBuilder fragment = new StringBuilder();
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(Long.MAX_VALUE);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                fragment.append(data);
                if (last) {
                    inbox.offer(fragment.toString());
                    fragment.setLength(0);
                }
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                inbox.offer("WS-ERROR: " + error);
            }
        };
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), listener).join();
        try {
            ws.sendText("{\"type\":\"JOIN\",\"pin\":\"" + pinCode + "\",\"name\":\"Solo\",\"role\":\"player\"}",
                    true);

            String start = null;
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                String msg = inbox.poll(Math.max(1, deadline - System.currentTimeMillis()),
                        TimeUnit.MILLISECONDS);
                if (msg == null) break;
                if (msg.startsWith("WS-ERROR")) fail(msg);
                if (msg.contains("\"ERROR\"")) fail("Join rejected: " + msg);
                if (msg.contains("QUESTION_START") && msg.contains("flow-q1")) {
                    start = msg;
                    break;
                }
            }
            assertNotNull(start, "Q1 QUESTION_START never arrived within 15s");
        } finally {
            ws.abort();
        }
    }
}
